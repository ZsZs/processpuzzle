package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.events.ProcessInstanceCompletedEvent;
import com.processpuzzle.workflow.execution.events.TaskCompletedEvent;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Completes an ACTIVE task: merges any additional context supplied by the caller, evaluates the
 * postcondition rule, and — only if it passes — runs the task's tool-backed steps, marks it
 * COMPLETED, advances the process via {@link TaskActivationService}, and closes out the process
 * instance if every task is now terminal.
 *
 * <p>A failed postcondition is <em>not</em> an error: per {@code CompleteTaskResponse} in
 * base-workflow-api.yaml, the endpoint still returns 200 with {@code accepted=false} and the
 * task stays ACTIVE so the user can address whatever the postcondition flagged and retry.
 */
@Component
@Transactional
public class CompleteTaskUseCase {

    public record Result(boolean accepted, TaskInstance task, String postconditionDetail) {
    }

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final RuleEvaluationPort ruleEvaluationPort;
    private final ToolStepExecutor toolStepExecutor;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteTaskUseCase(ProcessInstanceRepository processInstanceRepository,
                                ProcessDefinitionRepository processDefinitionRepository,
                                TaskInstanceRepository taskInstanceRepository,
                                RuleEvaluationPort ruleEvaluationPort,
                                ToolStepExecutor toolStepExecutor,
                                TaskActivationService taskActivationService,
                                ApplicationEventPublisher eventPublisher) {
        this.processInstanceRepository = processInstanceRepository;
        this.processDefinitionRepository = processDefinitionRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.ruleEvaluationPort = ruleEvaluationPort;
        this.toolStepExecutor = toolStepExecutor;
        this.taskActivationService = taskActivationService;
        this.eventPublisher = eventPublisher;
    }

    public Result complete(String orgKey, UUID processInstanceId, String taskDefinitionId, Map<String, Object> additionalContext) {
        ProcessInstance processInstance = processInstanceRepository.findByOrgKeyAndId(orgKey, processInstanceId)
                .orElseThrow(() -> new NotFoundException("No process instance with id '%s'".formatted(processInstanceId)));
        TaskInstance taskInstance = taskInstanceRepository
                .findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(orgKey, processInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in process instance '%s'".formatted(taskDefinitionId, processInstanceId)));

        if (taskInstance.getStatus() != TaskInstanceStatus.ACTIVE) {
            throw new ConflictException("Task '%s' is %s, not ACTIVE — cannot complete".formatted(taskDefinitionId, taskInstance.getStatus()));
        }

        ProcessDefinition definition = processDefinitionRepository
                .findByOrgKeyAndId(orgKey, processInstance.getProcessDefinitionId())
                .orElseThrow(() -> new NotFoundException("Process definition no longer exists"));
        TaskDefinition taskDef = definition.findTask(taskDefinitionId)
                .orElseThrow(() -> new NotFoundException("Task definition '%s' no longer exists".formatted(taskDefinitionId)));

        Map<String, Object> mergedContext = new HashMap<>(processInstance.getContext());
        if (additionalContext != null) {
            mergedContext.putAll(additionalContext);
        }

        RuleCheckResult postcondition = ruleEvaluationPort.evaluate(orgKey, taskDef.getPostconditionRuleId(), mergedContext);
        if (!postcondition.passed()) {
            return new Result(false, taskInstance, postcondition.detail());
        }

        processInstance.setContext(mergedContext);

        var stepResults = toolStepExecutor.execute(orgKey, taskDef.getSteps(), mergedContext);
        taskInstance.setStepResults(stepResults);
        taskInstance.setStatus(TaskInstanceStatus.COMPLETED);
        taskInstance.setCompletedAt(Instant.now());
        taskInstanceRepository.save(taskInstance);

        // mergedContext may have been enriched further by tool output mappings during step execution
        processInstance.setContext(mergedContext);
        processInstanceRepository.save(processInstance);

        eventPublisher.publishEvent(new TaskCompletedEvent(orgKey, processInstanceId, taskInstance.getId(), taskDefinitionId));

        taskActivationService.activateEligibleTasks(orgKey, definition, processInstanceId, mergedContext);

        if (taskActivationService.allTerminal(orgKey, processInstanceId)) {
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
            processInstance.setCompletedAt(Instant.now());
            processInstanceRepository.save(processInstance);
            eventPublisher.publishEvent(new ProcessInstanceCompletedEvent(orgKey, processInstanceId, definition.getId()));
        }

        return new Result(true, taskInstance, null);
    }
}
