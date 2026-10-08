package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceCancelledEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cancels a running instance: the instance, every task not yet terminal and every event still able to
 * move become CANCELLED, and every timer is disarmed, so that nothing is delivered to the instance or
 * fires for it any more.
 */
@Component
@Transactional
public class CancelWorkflowInstanceUseCase {

    private static final Set<TaskInstanceStatus> OPEN_TASK =
            EnumSet.of(TaskInstanceStatus.PENDING, TaskInstanceStatus.ACTIVE, TaskInstanceStatus.BLOCKED);

    private final WorkflowInstanceRepository repository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final EventInstanceRepository eventInstanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelWorkflowInstanceUseCase(WorkflowInstanceRepository repository,
                                         TaskInstanceRepository taskInstanceRepository,
                                         EventInstanceRepository eventInstanceRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.eventPublisher = eventPublisher;
    }

    public void cancel(String orgKey, UUID instanceId, String reason) {
        WorkflowInstance instance = repository.findByOrgKeyAndId(orgKey, instanceId)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(instanceId)));

        if (instance.getStatus() == WorkflowInstanceStatus.COMPLETED || instance.getStatus() == WorkflowInstanceStatus.CANCELLED) {
            throw new ConflictException("Workflow instance '%s' is already %s".formatted(instanceId, instance.getStatus()));
        }
        Instant now = Instant.now();
        instance.setStatus(WorkflowInstanceStatus.CANCELLED);
        instance.setCompletedAt(now);
        repository.save(instance);

        taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instanceId).stream()
                .filter(task -> OPEN_TASK.contains(task.getStatus()))
                .forEach(task -> {
                    task.setStatus(TaskInstanceStatus.CANCELLED);
                    task.setCancelledAt(now);
                    task.setCancelReason(reason == null || reason.isBlank() ? "workflow instance cancelled" : reason);
                    taskInstanceRepository.save(task);
                });

        // A cancelled run waits for nothing any more: withdraw its catches, so no event is delivered to it,
        // and disarm its timers, so none fires for it.
        eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instanceId).stream()
                .filter(event -> !event.getStatus().isTerminal() || event.getDueAt() != null)
                .forEach(event -> {
                    if (!event.getStatus().isTerminal()) {
                        event.setStatus(EventInstanceStatus.CANCELLED);
                    }
                    event.setDueAt(null);
                    eventInstanceRepository.save(event);
                });

        eventPublisher.publishEvent(new WorkflowInstanceCancelledEvent(orgKey, instanceId, instance.getWorkflowId(), reason));
    }
}
