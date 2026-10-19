package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.engine.CalculationAnalysis;
import com.processpuzzle.calculation.engine.PlannedDecision;
import com.processpuzzle.calculation.engine.TraceEntry;
import com.processpuzzle.calculation.model.CalculationParameter;
import com.processpuzzle.calculation.model.DecisionDependency;
import com.processpuzzle.calculation.model.DecisionNode;
import com.processpuzzle.calculation.model.EvaluationResult;
import com.processpuzzle.calculation.model.RunStatus;
import com.processpuzzle.calculation.model.ValidationReport;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.EvaluateCalculation;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import com.processpuzzle.shared.model.ImportResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Between the generated {@code com.processpuzzle.calculation.model} DTOs and the domain. */
@Component
public class CalculationMapper {

    public com.processpuzzle.calculation.model.CalculationDefinition toModel(CalculationDefinition definition) {
        CalculationModel model = definition.parsedModel();
        return new com.processpuzzle.calculation.model.CalculationDefinition()
                .id(definition.getId())
                .name(definition.getName())
                .description(definition.getDescription())
                .source(definition.getSource())
                .definitionVersion(definition.getDefinitionVersion())
                .parameters(model.parameters().entrySet().stream()
                        .map(entry -> new CalculationParameter().name(entry.getKey())
                                .entity(entry.getValue().entity()).type(entry.getValue().type()))
                        .toList())
                .outputEntity(definition.getOutputEntity())
                .version(definition.getVersion())
                .createdAt(toOffsetDateTime(definition.getCreatedAt()))
                .updatedAt(toOffsetDateTime(definition.getUpdatedAt()));
    }

    public ValidationReport toReport(CalculationValidator.Validation validation) {
        ValidationReport report = new ValidationReport().valid(validation.isValid()).errors(validation.errors());
        CalculationAnalysis analysis = validation.analysis();
        if (analysis != null) {
            report.order(analysis.order())
                    .decisions(analysis.decisions().stream().map(CalculationMapper::toNode).toList())
                    .dependencies(analysis.dependencies().stream()
                            .map(edge -> new DecisionDependency().from(edge.from()).to(edge.to())).toList());
        }
        return report;
    }

    private static DecisionNode toNode(PlannedDecision decision) {
        return new DecisionNode()
                .name(decision.name())
                .kind(decision.kind() == null ? null : DecisionNode.KindEnum.fromValue(decision.kind().name()))
                .scope(decision.scope() == null ? "?" : decision.scope().toString());
    }

    public EvaluationResult toModel(EvaluateCalculation.Evaluation evaluation) {
        com.processpuzzle.calculation.engine.EvaluationResult result = evaluation.result();
        return new EvaluationResult()
                .values(new LinkedHashMap<>(result.values()))
                .rows(new LinkedHashMap<>(result.rows()))
                .groups(new LinkedHashMap<>(result.groups()))
                .order(result.order())
                .trace(result.trace().stream().map(CalculationMapper::toModel).toList())
                .error(evaluation.error());
    }

    private static com.processpuzzle.calculation.model.TraceEntry toModel(TraceEntry entry) {
        return new com.processpuzzle.calculation.model.TraceEntry()
                .decision(entry.decision())
                .kind(entry.kind() == null ? null : entry.kind().name())
                .scope(entry.scope())
                .value(entry.value())
                .error(entry.error());
    }

    /** A run without its trace, which {@code /trace} answers. */
    @SuppressWarnings("unchecked")
    public com.processpuzzle.calculation.model.CalculationRun toModel(CalculationRun run) {
        Map<String, Object> result = run.getResult() == null ? Map.of() : run.getResult();
        return new com.processpuzzle.calculation.model.CalculationRun()
                .id(run.getId())
                .calculationId(run.getCalculationId())
                .definitionVersion(run.getDefinitionVersion())
                .channel(com.processpuzzle.calculation.model.CalculationRun.ChannelEnum.fromValue(run.getChannel().name()))
                .triggeredBy(com.processpuzzle.calculation.model.CalculationRun.TriggeredByEnum.fromValue(run.getTriggeredBy().name()))
                .subjectType(run.getSubjectType())
                .subjectId(run.getSubjectId())
                .parameters(new LinkedHashMap<>(run.getParameters() == null ? Map.of() : run.getParameters()))
                .status(RunStatus.fromValue(run.getStatus().name()))
                .error(run.getError())
                .result(new EvaluationResult()
                        .values(new LinkedHashMap<>((Map<String, Object>) result.getOrDefault("values", Map.of())))
                        .rows(new LinkedHashMap<>((Map<String, Object>) result.getOrDefault("rows", Map.of())))
                        .groups(new LinkedHashMap<>((Map<String, Object>) result.getOrDefault("groups", Map.of())))
                        .order((List<String>) result.getOrDefault("order", List.of())))
                .written(run.getWritten())
                .frozen(run.isFrozen())
                .frozenAt(toOffsetDateTime(run.getFrozenAt()))
                .definitionSource(run.getDefinitionSource())
                .startedAt(toOffsetDateTime(run.getStartedAt()))
                .completedAt(toOffsetDateTime(run.getCompletedAt()));
    }

    public List<com.processpuzzle.calculation.model.TraceEntry> toTrace(CalculationRun run) {
        return (run.getTrace() == null ? List.<Map<String, Object>>of() : run.getTrace()).stream()
                .map(entry -> new com.processpuzzle.calculation.model.TraceEntry()
                        .decision((String) entry.get("decision"))
                        .kind((String) entry.get("kind"))
                        .scope((String) entry.get("scope"))
                        .value(entry.get("value"))
                        .error((String) entry.get("error")))
                .toList();
    }

    public ImportResult toImportResult(ImportOutcome outcome) {
        return new ImportResult().created(outcome.created()).updated(outcome.updated()).errors(outcome.errors());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
