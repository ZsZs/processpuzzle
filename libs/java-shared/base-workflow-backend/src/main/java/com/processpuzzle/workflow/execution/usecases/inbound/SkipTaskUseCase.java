package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedProcess;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.events.ProcessInstanceCompletedEvent;
import com.processpuzzle.workflow.execution.events.TaskSkippedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import com.processpuzzle.workflow.execution.domain.ProcessContext;
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

    private final ProcessInstanceRepository processInstanceRepository;
    private final ResolveProcessDefinitionUseCase resolveProcessDefinition;
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public SkipTaskUseCase(ProcessInstanceRepository processInstanceRepository,
                            ResolveProcessDefinitionUseCase resolveProcessDefinition,
                            TaskInstanceRepository taskInstanceRepository,
                            TaskActivationService taskActivationService,
                            ApplicationEventPublisher eventPublisher) {
        this.processInstanceRepository = processInstanceRepository;
        this.resolveProcessDefinition = resolveProcessDefinition;
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskActivationService = taskActivationService;
        this.eventPublisher = eventPublisher;
    }

    public TaskInstance skip(String orgKey, UUID processInstanceId, String taskDefinitionId, String reason) {
        ProcessInstance processInstance = processInstanceRepository.findByOrgKeyAndId(orgKey, processInstanceId)
                .orElseThrow(() -> new NotFoundException("No process instance with id '%s'".formatted(processInstanceId)));
        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(orgKey, processInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in process instance '%s'".formatted(taskDefinitionId, processInstanceId)));

        if (taskInstance.getStatus() == TaskInstanceStatus.COMPLETED || taskInstance.getStatus() == TaskInstanceStatus.SKIPPED) {
            throw new ConflictException("Task '%s' is already %s".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        taskInstance.setStatus(TaskInstanceStatus.SKIPPED);
        taskInstance.setSkippedAt(Instant.now());
        taskInstanceRepository.save(taskInstance);

        eventPublisher.publishEvent(new TaskSkippedEvent(orgKey, processInstanceId, taskInstance.getId(), taskDefinitionId, reason));

        ResolvedProcess definition =
                resolveProcessDefinition.resolveByOrgKeyAndId(orgKey, processInstance.getProcessDefinitionId());
        // Skipping contributes nothing of its own, but the tasks it unblocks are guarded against the
        // context as it stands, so it has to be the assembled one and not just the initial values.
        Map<String, Object> context = ProcessContext.assemble(
                processInstance, taskInstanceRepository.findByOrgKeyAndProcessInstanceId(orgKey, processInstanceId));
        taskActivationService.activateEligibleTasks(orgKey, definition, processInstanceId, context);

        if (taskActivationService.allTerminal(orgKey, processInstanceId)) {
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
            processInstance.setCompletedAt(Instant.now());
            processInstanceRepository.save(processInstance);
            eventPublisher.publishEvent(new ProcessInstanceCompletedEvent(orgKey, processInstanceId, definition.id()));
        }

        return taskInstance;
    }
}
