package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.events.TaskSkippedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;
import java.util.Map;

/**
 * Manager override: forces a task straight to SKIPPED regardless of its current (non-terminal)
 * status, bypassing precondition/postcondition entirely. base-workflow-api.yaml declares no 409
 * response for this operation — unlike complete/assign, skip is allowed from PENDING, BLOCKED, or
 * ACTIVE alike.
 */
@Component
@Transactional
public class SkipTaskUseCase {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ResolveWorkflowUseCase resolveWorkflow;
    private final TaskInstanceRepository taskInstanceRepository;
    private final EventInstanceRepository eventInstanceRepository;
    private final WorkflowProgression progression;
    private final ApplicationEventPublisher eventPublisher;

    public SkipTaskUseCase(WorkflowInstanceRepository workflowInstanceRepository,
                            ResolveWorkflowUseCase resolveWorkflow,
                            TaskInstanceRepository taskInstanceRepository,
                            EventInstanceRepository eventInstanceRepository,
                            WorkflowProgression progression,
                            ApplicationEventPublisher eventPublisher) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.resolveWorkflow = resolveWorkflow;
        this.taskInstanceRepository = taskInstanceRepository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.progression = progression;
        this.eventPublisher = eventPublisher;
    }

    public TaskInstance skip(String orgKey, UUID workflowInstanceId, String taskDefinitionId, String reason) {
        WorkflowInstance workflowInstance = workflowInstanceRepository.findByOrgKeyAndId(orgKey, workflowInstanceId)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(workflowInstanceId)));
        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, workflowInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in workflow instance '%s'".formatted(taskDefinitionId, workflowInstanceId)));

        if (taskInstance.getStatus() == TaskInstanceStatus.COMPLETED || taskInstance.getStatus() == TaskInstanceStatus.SKIPPED
                || taskInstance.getStatus() == TaskInstanceStatus.CANCELLED) {
            throw new ConflictException("Task '%s' is already %s".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        taskInstance.setStatus(TaskInstanceStatus.SKIPPED);
        taskInstance.setSkippedAt(Instant.now());
        taskInstanceRepository.save(taskInstance);

        eventPublisher.publishEvent(new TaskSkippedEvent(orgKey, workflowInstanceId, taskInstance.getId(), taskDefinitionId, reason));

        ResolvedWorkflow definition =
                resolveWorkflow.resolveByOrgKeyAndId(orgKey, workflowInstance.getWorkflowId());
        // Skipping contributes nothing of its own, but the tasks it unblocks are guarded against the
        // context as it stands, so it has to be the assembled one and not just the initial values.
        Map<String, Object> context = WorkflowContext.assemble(
                workflowInstance, taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId),
                eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId));
        progression.advance(orgKey, definition, workflowInstance, context);

        return taskInstance;
    }
}
