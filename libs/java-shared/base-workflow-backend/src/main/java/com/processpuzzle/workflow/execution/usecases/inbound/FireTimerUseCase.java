package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.TimerExpressions;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.PayloadPath;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.TimerFiredEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires one due timer catch, as {@code TimerSweep} finds them.
 *
 * <p>First it re-checks what the sweep read outside any transaction: that the timer is still due, its
 * instance still ACTIVE, and — for a boundary event — its task still ACTIVE. A timer that no longer has
 * anything to fire for has its {@code dueAt} cleared, and a boundary still waiting is CANCELLED, so the
 * sweep does not find it again.
 *
 * <p>A firing counts in {@code fireCount} and publishes {@link TimerFiredEvent}. The first firing also
 * occurs the catch through {@link CatchOccurrence} — which interrupts the task of an interrupting
 * boundary and moves the instance on. A CYCLE with firings left is then re-armed one interval later;
 * the validator only allows one on a non-interrupting boundary, so its task is still running. Anything
 * else is done: {@code dueAt} is cleared.
 *
 * <p>{@code REQUIRES_NEW}, and guarded by the row's optimistic lock: the sweep fires each timer in a
 * transaction of its own, and two sweeps — two replicas — reaching the same row fire it once; the
 * other fails on the lock and finds the row moved on next time.
 */
@Component
public class FireTimerUseCase {

    private final EventInstanceRepository eventInstanceRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final ResolveWorkflowUseCase resolveWorkflow;
    private final CatchOccurrence catchOccurrence;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FireTimerUseCase(EventInstanceRepository eventInstanceRepository,
                            WorkflowInstanceRepository workflowInstanceRepository,
                            TaskInstanceRepository taskInstanceRepository,
                            ResolveWorkflowUseCase resolveWorkflow,
                            CatchOccurrence catchOccurrence,
                            ApplicationEventPublisher eventPublisher,
                            Clock clock) {
        this.eventInstanceRepository = eventInstanceRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.resolveWorkflow = resolveWorkflow;
        this.catchOccurrence = catchOccurrence;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** @return whether the timer fired */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fire(String orgKey, UUID eventInstanceId) {
        EventInstance event = eventInstanceRepository.findByOrgKeyAndId(orgKey, eventInstanceId).orElse(null);
        Instant now = clock.instant();
        if (event == null || event.getDueAt() == null || event.getDueAt().isAfter(now)) {
            return false;
        }
        WorkflowInstance instance =
                workflowInstanceRepository.findByOrgKeyAndId(orgKey, event.getWorkflowInstanceId()).orElse(null);
        if (instance == null || instance.getStatus() != WorkflowInstanceStatus.ACTIVE) {
            return disarm(event);
        }
        ResolvedWorkflow definition = resolveWorkflow.resolveByOrgKeyAndId(orgKey, instance.getWorkflowId());
        EventUse use = definition.definition().findEventUse(event.getEventUseId()).filter(EventUse::hasTimer).orElse(null);
        if (use == null || (use.isBoundary() && !taskIsActive(orgKey, instance, use))) {
            return disarm(event);
        }
        boolean first = event.getStatus() == EventInstanceStatus.WAITING;
        if (!first && event.getStatus() != EventInstanceStatus.OCCURRED) {
            return disarm(event);
        }

        Instant dueAt = event.getDueAt();
        event.setFireCount(event.firings() + 1);
        eventPublisher.publishEvent(new TimerFiredEvent(orgKey, instance.getId(), event.getId(), use.getId(),
                event.getFireCount(), dueAt, now));

        Optional<TimerExpressions.Arming> next = Optional.empty();
        if (use.isBoundary() && !use.isInterrupting()) {
            Map<String, Object> context = contextOf(orgKey, instance);
            next = TimerExpressions.next(use.getTimer(), path -> PayloadPath.resolve(context, path), dueAt,
                    event.getRemainingFirings());
        }
        // Not Optional.map: an unbounded cycle's null remaining count would come out as empty.
        if (next.isPresent()) {
            event.setDueAt(next.get().dueAt());
            event.setRemainingFirings(next.get().remainingFirings());
        } else {
            event.setDueAt(null);
        }

        if (first) {
            catchOccurrence.occur(orgKey, definition, instance, event, now, event.getId(), null, Map.of());
        } else {
            eventInstanceRepository.save(event);
        }
        return true;
    }

    private boolean taskIsActive(String orgKey, WorkflowInstance instance, EventUse boundary) {
        return taskInstanceRepository
                .findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, instance.getId(), boundary.getAttachedTo())
                .map(TaskInstance::getStatus)
                .filter(status -> status == TaskInstanceStatus.ACTIVE)
                .isPresent();
    }

    private Map<String, Object> contextOf(String orgKey, WorkflowInstance instance) {
        return WorkflowContext.assemble(instance,
                taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instance.getId()),
                eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instance.getId()));
    }

    /** Nothing to fire for any more: stop the sweep from finding the row again. */
    private boolean disarm(EventInstance event) {
        event.setDueAt(null);
        if (event.getStatus() == EventInstanceStatus.WAITING || event.getStatus() == EventInstanceStatus.PENDING) {
            event.setStatus(EventInstanceStatus.CANCELLED);
        }
        eventInstanceRepository.save(event);
        return false;
    }
}
