package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.event.CalculationCompleted;
import com.processpuzzle.calculation.event.CalculationFailed;
import com.processpuzzle.calculation.event.CalculationFrozen;
import com.processpuzzle.shared.event.PlatformEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Announces a run: the module's own event, and the same fact as a {@link PlatformEvent} of subject type
 * {@value #SUBJECT_TYPE} entering the run's state — {@code COMPLETED}, {@code FAILED} or {@code FROZEN} — which
 * base-event's catalog can give a name. Called inside the transaction that stores the run.
 */
@Component
public class RunEvents {

    public static final String SUBJECT_TYPE = "calculation-run";
    /** The key of the single values in a run's result, and in the facts a run publishes. */
    public static final String VALUES = "values";
    /** The payload attributes of every fact a run publishes. */
    public static final Set<String> FACT_ATTRIBUTES = Set.of("calculationId", "definitionVersion", "subjectType", "subjectId", VALUES);

    private final ApplicationEventPublisher publisher;

    public RunEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @SuppressWarnings("unchecked")
    public void ran(CalculationRun run) {
        Instant at = run.getCompletedAt();
        if (run.getStatus() == RunStatus.COMPLETED) {
            Map<String, Object> values = (Map<String, Object>) run.getResult().getOrDefault(VALUES, Map.of());
            publisher.publishEvent(new CalculationCompleted(run.getOrgKey(), run.getCalculationId(), run.getId(),
                    run.getSubjectType(), run.getSubjectId(), values, at));
        } else {
            publisher.publishEvent(new CalculationFailed(run.getOrgKey(), run.getCalculationId(), run.getId(),
                    run.getSubjectType(), run.getSubjectId(), run.getError(), at));
        }
        platformFact(run, run.getStatus().name(), at);
    }

    public void frozen(CalculationRun run) {
        publisher.publishEvent(new CalculationFrozen(run.getOrgKey(), run.getCalculationId(), run.getId(),
                run.getSubjectType(), run.getSubjectId(), run.getFrozenAt()));
        platformFact(run, "FROZEN", run.getFrozenAt());
    }

    private void platformFact(CalculationRun run, String state, Instant at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("calculationId", run.getCalculationId());
        payload.put("definitionVersion", run.getDefinitionVersion());
        payload.put("subjectType", run.getSubjectType());
        payload.put("subjectId", run.getSubjectId());
        payload.put(VALUES, run.getResult().get(VALUES));
        publisher.publishEvent(PlatformEvent.stateChanged(run.getOrgKey(), SUBJECT_TYPE, run.getId().toString(), state,
                payload, at));
    }
}
