package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.ParameterSpec;
import com.processpuzzle.calculation.domain.definition.TriggerSpec;
import com.processpuzzle.calculation.engine.CalculationEngine;
import com.processpuzzle.calculation.engine.CalculationException;
import com.processpuzzle.calculation.engine.CalculationPlan;
import com.processpuzzle.calculation.engine.EvaluationResult;
import com.processpuzzle.calculation.engine.TraceEntry;
import com.processpuzzle.calculation.engine.Values;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.calculation.usecase.exception.InvalidParametersException;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.definition.Channel;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Computes a calculation, writes its output and keeps the run.
 *
 * <p>Two transactions, on purpose. Reading, computing and writing the output happen in a transaction of their
 * own, so a failure leaves no output half-written — a group output writes several objects — and a failure inside
 * the entity layer, which marks whatever transaction it ran in rollback-only, cannot take the run's record down
 * with it. The run is then stored in the caller's transaction, a failed one too: it says why a stored value did
 * not move.
 */
@Service
public class RunCalculation {

    private static final Logger LOG = LoggerFactory.getLogger(RunCalculation.class);
    /** The width of the run's error column. */
    private static final int MAX_ERROR = 4000;

    private final FindCalculationDefinition definitions;
    private final CalculationValidator validator;
    private final InputResolver resolver;
    private final CalculationEngine engine;
    private final OutputWriter writer;
    private final CalculationRunRepository runs;
    private final RunEvents events;
    private final TransactionTemplate computing;
    private final TransactionTemplate storing;

    public RunCalculation(FindCalculationDefinition definitions, CalculationValidator validator, InputResolver resolver,
                          CalculationEngine engine, OutputWriter writer, CalculationRunRepository runs, RunEvents events,
                          PlatformTransactionManager transactionManager) {
        this.definitions = definitions;
        this.validator = validator;
        this.resolver = resolver;
        this.engine = engine;
        this.writer = writer;
        this.runs = runs;
        this.events = events;
        this.computing = new TransactionTemplate(transactionManager);
        this.computing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.storing = new TransactionTemplate(transactionManager);
    }

    /** The object a run is about: what its output writes, else what its first entity parameter names. */
    public record Subject(String type, String id) {
    }

    /**
     * @throws SubjectFrozenException      when the subject's result is frozen
     * @throws InvalidParametersException  on a request — {@code MANUAL} — whose parameters do not resolve; a trigger's
     *                                     are recorded as a failed run instead
     */
    public CalculationRun run(String orgKey, String calculationId, Channel channel, Map<String, Object> parameters,
                              RunTrigger trigger) {
        CalculationDefinition definition = definitions.find(orgKey, calculationId, channel);
        CalculationModel model = definition.parsedModel();
        CalculationValidator.Validation validation = validator.validate(model);
        if (!validation.isValid()) {
            throw new InvalidCalculationException(calculationId, validation.errors());
        }
        Subject subject = subjectOf(model, parameters);
        if (subject != null && isFrozen(orgKey, calculationId, subject.id())) {
            throw new SubjectFrozenException(calculationId, subject.type(), subject.id());
        }

        CalculationRun run = CalculationRun.builder()
                .id(UUID.randomUUID()).orgKey(orgKey).calculationId(calculationId)
                .definitionVersion(definition.getDefinitionVersion()).channel(channel)
                .definitionSource(definition.getSource()).triggeredBy(trigger)
                .subjectType(subject == null ? null : subject.type()).subjectId(subject == null ? null : subject.id())
                .parameters(Values.plainMap(parameters == null ? Map.of() : parameters))
                .startedAt(Instant.now())
                .build();
        compute(orgKey, model, validation.analysis().plan(), parameters, trigger, run);
        run.setCompletedAt(Instant.now());

        return storing.execute(status -> {
            CalculationRun stored = runs.save(run);
            events.ran(stored);
            TriggerSpec triggerSpec = model.trigger();
            if (stored.getStatus() == RunStatus.COMPLETED && triggerSpec != null && triggerSpec.freeze() != null
                    && triggerSpec.freeze().onCompletion()) {
                stored.freeze(stored.getCompletedAt());
                events.frozen(stored);
            }
            return stored;
        });
    }

    private void compute(String orgKey, CalculationModel model, CalculationPlan plan, Map<String, Object> parameters,
                         RunTrigger trigger, CalculationRun run) {
        try {
            Computed computed = computing.execute(status -> {
                ResolvedInputs inputs = resolver.resolve(orgKey, model, parameters);
                EvaluationResult result = engine.evaluate(plan, inputs.parameters(), inputs.inputs());
                int written = writer.write(orgKey, model, inputs, result);
                return new Computed(inputs, result, written);
            });
            EvaluationResult plain = computed.result().plain();
            run.setStatus(RunStatus.COMPLETED);
            run.setParameters(Values.plainMap(computed.inputs().supplied()));
            run.setResult(resultOf(plain));
            run.setTrace(traceOf(plain.trace()));
            run.setWritten(computed.written());
        } catch (CalculationException e) {
            failed(run, e.getMessage(), e.trace());
        } catch (InvalidParametersException e) {
            if (trigger == RunTrigger.MANUAL) {
                throw e;
            }
            failed(run, e.getMessage(), List.of());
        } catch (RuntimeException e) {
            LOG.warn("Run of calculation '{}' in '{}' failed: {}", run.getCalculationId(), orgKey, e.getMessage());
            failed(run, e.getMessage(), List.of());
        }
    }

    public boolean isFrozen(String orgKey, String calculationId, String subjectId) {
        return runs.existsByOrgKeyAndCalculationIdAndSubjectIdAndFrozenTrue(orgKey, calculationId, subjectId);
    }

    /** The subject of a run of {@code model} with these parameters; null when it has no entity parameter. */
    public static Subject subjectOf(CalculationModel model, Map<String, Object> parameters) {
        String name = model.output() != null && !model.output().isGrouped() ? OutputWriter.targetOf(model) : null;
        if (name == null) {
            name = model.parameters().entrySet().stream().filter(entry -> entry.getValue().isEntity())
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
        ParameterSpec parameter = name == null ? null : model.parameters().get(name);
        Object value = parameters == null || name == null ? null : parameters.get(name);
        if (parameter == null || value == null) {
            return null;
        }
        String id = value instanceof Map<?, ?> map && map.get("id") != null ? map.get("id").toString() : value.toString();
        return new Subject(parameter.entity(), id);
    }

    private static void failed(CalculationRun run, String error, List<TraceEntry> trace) {
        run.setStatus(RunStatus.FAILED);
        run.setError(error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR));
        run.setTrace(traceOf(trace.stream().map(entry -> new TraceEntry(entry.decision(), entry.kind(), entry.scope(),
                Values.plain(entry.value()), entry.error())).toList()));
    }

    static Map<String, Object> resultOf(EvaluationResult plain) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(RunEvents.VALUES, plain.values());
        result.put("rows", plain.rows());
        result.put("groups", plain.groups());
        result.put("order", plain.order());
        return result;
    }

    static List<Map<String, Object>> traceOf(List<TraceEntry> trace) {
        return trace.stream().map(entry -> {
            Map<String, Object> plain = new LinkedHashMap<>();
            plain.put("decision", entry.decision());
            plain.put("kind", entry.kind() == null ? null : entry.kind().name());
            plain.put("scope", entry.scope());
            plain.put("value", entry.value());
            plain.put("error", entry.error());
            return plain;
        }).toList();
    }

    private record Computed(ResolvedInputs inputs, EvaluationResult result, int written) {
    }
}
