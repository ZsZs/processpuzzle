package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
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
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public SkipTaskUseCase(ProcessInstanceRepository processInstanceRepository,
                            ProcessDefinitionRepository processDefinitionRepository,
                            TaskInstanceRepository taskInstanceRepository,
                            TaskActivationService taskActivationService,
                            ApplicationEventPublisher eventPublisher) {
        this.processInstanceRepository = processInstanceRepository;
        this.processDefinitionRepository = processDefinitionRepository;
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

        ProcessDefinition definition = processDefinitionRepository
                .findByOrgKeyAndId(orgKey, processInstance.getProcessDefinitionId())
                .orElseThrow(() -> new NotFoundException("Process definition no longer exists"));
        taskActivationService.activateEligibleTasks(orgKey, definition, processInstanceId, processInstance.getContext());

        if (taskActivationService.allTerminal(orgKey, processInstanceId)) {
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
            processInstance.setCompletedAt(Instant.now());
            processInstanceRepository.save(processInstance);
            eventPublisher.publishEvent(new ProcessInstanceCompletedEvent(orgKey, processInstanceId, definition.getId()));
        }

        return taskInstance;
    }
}
