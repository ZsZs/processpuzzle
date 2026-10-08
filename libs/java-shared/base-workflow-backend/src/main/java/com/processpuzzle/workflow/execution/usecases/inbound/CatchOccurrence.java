package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.events.TaskInterruptedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * What happens when a waiting catch is reached by its event — the shared part of a catalogued event
 * being delivered ({@link OccurCatchEventUseCase}) and a timer firing ({@link FireTimerUseCase}).
 *
 * <p>Marks the catch OCCURRED with what it received and contributed. If the catch is an
 * <em>interrupting</em> boundary event, the task it is attached to is cancelled — when still ACTIVE —
 * and {@link TaskInterruptedEvent} is published; a non-interrupting one leaves the task running. Then
 * the instance is moved on through {@link WorkflowProgression}: the boundary's dependents become
 * eligible, and the interrupted task's own successors are cancelled as unreachable by the engine's
 * dead-path elimination.
 *
 * <p>Does not touch {@code dueAt}: whether a timer fires again is the caller's to decide. Runs in the
 * caller's transaction; the caller has checked the catch still waits and the instance is ACTIVE.
 */
@Component
public class CatchOccurrence {

    private final EventInstanceRepository eventInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final WorkflowProgression progression;
    private final ApplicationEventPublisher eventPublisher;

    public CatchOccurrence(EventInstanceRepository eventInstanceRepository,
                           TaskInstanceRepository taskInstanceRepository,
                           WorkflowProgression progression,
                           ApplicationEventPublisher eventPublisher) {
        this.eventInstanceRepository = eventInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.progression = progression;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param occurrenceId the occurrence delivered; a timer passes its own row's id
     * @param payload      what the catch received; null for a timer
     * @param contribution what the catch's payload mapping took from it into the context
     */
    public void occur(String orgKey, ResolvedWorkflow definition, WorkflowInstance instance, EventInstance event,
                      Instant at, UUID occurrenceId, Map<String, Object> payload, Map<String, Object> contribution) {
        event.setStatus(EventInstanceStatus.OCCURRED);
        event.setOccurredAt(at);
        event.setOccurrenceId(occurrenceId);
        event.setPayload(payload);
        event.setContextContribution(contribution);
        eventInstanceRepository.save(event);

        definition.definition().findEventUse(event.getEventUseId())
                .filter(use -> use.isBoundary() && use.isInterrupting())
                .ifPresent(use -> interrupt(orgKey, instance, use, at));

        Map<String, Object> context = WorkflowContext.assemble(instance,
                taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instance.getId()),
                eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instance.getId()));
        progression.advance(orgKey, definition, instance, context);
    }

    private void interrupt(String orgKey, WorkflowInstance instance, EventUse boundary, Instant at) {
        taskInstanceRepository.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, instance.getId(),
                        boundary.getAttachedTo())
                .filter(task -> task.getStatus() == TaskInstanceStatus.ACTIVE)
                .ifPresent(task -> {
                    task.setStatus(TaskInstanceStatus.CANCELLED);
                    task.setCancelledAt(at);
                    task.setCancelReason("interrupted by " + boundary.getId());
                    taskInstanceRepository.save(task);
                    eventPublisher.publishEvent(new TaskInterruptedEvent(orgKey, instance.getId(), task.getId(),
                            task.getTaskDefinitionId(), boundary.getId()));
                });
    }
}
