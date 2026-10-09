package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceCompletedEvent;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Moves a running instance on after one of its nodes changed — a task completed or skipped, a catch
 * event occurred — and closes it once nothing is left to move. The shared tail of
 * {@code CompleteTaskUseCase}, {@code SkipTaskUseCase} and {@code OccurCatchEventUseCase}.
 *
 * <p>The close-out is serialized per instance: the instance row is locked before the check, so two
 * transactions finishing its last two nodes concurrently decide one after the other, and the second
 * sees what the first committed. Without the lock each saw the other's node as still running under
 * READ_COMMITTED, neither closed the instance, and it stayed ACTIVE with nothing left to do.
 *
 * <p>Runs in the caller's transaction.
 */
@Component
public class WorkflowProgression {

    private final TaskActivationService taskActivationService;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowProgression(TaskActivationService taskActivationService,
                               WorkflowInstanceRepository workflowInstanceRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.taskActivationService = taskActivationService;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Activates what is now eligible, then closes {@code instance} if every node is terminal.
     *
     * @param context the instance's context as it now stands — what newly eligible tasks are guarded
     *                against and throws take their values from
     * @return whether the instance was completed by this call
     */
    public boolean advance(String orgKey, ResolvedWorkflow definition, WorkflowInstance instance,
                           Map<String, Object> context) {
        taskActivationService.activateEligibleTasks(orgKey, definition, instance.getId(), context);

        WorkflowInstance locked = workflowInstanceRepository.lockForCloseOut(orgKey, instance.getId()).orElse(instance);
        if (locked.getStatus() != WorkflowInstanceStatus.ACTIVE
                || !taskActivationService.allTerminal(orgKey, definition, locked.getId())) {
            return false;
        }
        locked.setStatus(WorkflowInstanceStatus.COMPLETED);
        locked.setCompletedAt(Instant.now());
        workflowInstanceRepository.save(locked);
        eventPublisher.publishEvent(new WorkflowInstanceCompletedEvent(orgKey, locked.getId(), definition.id()));
        return true;
    }
}
