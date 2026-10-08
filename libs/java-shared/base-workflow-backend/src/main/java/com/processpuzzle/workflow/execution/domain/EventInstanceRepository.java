package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.definition.domain.EventDirection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Derived finders only: the library tests run on H2, so nothing here may query into JSONB. */
public interface EventInstanceRepository extends JpaRepository<EventInstance, UUID> {

    Optional<EventInstance> findByOrgKeyAndId(String orgKey, UUID id);

    List<EventInstance> findByOrgKeyAndWorkflowInstanceId(String orgKey, UUID workflowInstanceId);

    /** The events of one page of instances, for the list endpoint. */
    List<EventInstance> findByOrgKeyAndWorkflowInstanceIdIn(String orgKey, Collection<UUID> workflowInstanceIds);

    /** Catches of an event in a status — SIGNAL delivery. */
    List<EventInstance> findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusOrderByWaitingSinceAscIdAsc(
            String orgKey, String eventDefinitionId, EventDirection direction, EventInstanceStatus status);

    /** Catches of an event in a status waiting for one correlation value — SYSTEM and MESSAGE delivery. */
    List<EventInstance> findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusAndCorrelationValueOrderByWaitingSinceAscIdAsc(
            String orgKey, String eventDefinitionId, EventDirection direction, EventInstanceStatus status,
            String correlationValue);

    /** Live events still able to move — the close-out check; a count for the reason the task one is. */
    long countByOrgKeyAndWorkflowInstanceIdAndEventUseIdInAndStatusIn(
            String orgKey, UUID workflowInstanceId, Collection<String> eventUseIds,
            Collection<EventInstanceStatus> statuses);

    /** The timers due by {@code now}, across organizations, earliest first — one page of {@code TimerSweep}. */
    List<EventInstance> findTop100ByDueAtLessThanEqualOrderByDueAtAsc(Instant now);

    /** Whether an occurrence was already delivered to a catch — a MESSAGE redelivery. */
    boolean existsByOrgKeyAndOccurrenceIdAndDirection(String orgKey, UUID occurrenceId, EventDirection direction);
}
