package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedProcess;
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
import com.processpuzzle.workflow.execution.domain.ProcessContext;

/**
 * Completes an ACTIVE task: merges any additional context supplied by the caller, evaluates the
 * postcondition rule, and — only if it passes — runs the task's tool-backed steps, marks it
 * COMPLETED, advances the process via {@link TaskActivationService}, and closes out the process
 * instance if every task is now terminal.
 *
 * <p>Only the task row is written on the way through. What the completion added to the process
 * context is recorded on the task as its {@code contextContribution} and folded back in on read by
 * {@link ProcessContext}, rather than accumulated on the process instance — otherwise every
 * completion would contend on the instance's optimistic lock and {@code parallel} tasks could not
 * be completed concurrently. The instance is touched only for the terminal transition, which by
 * definition only one completion performs.
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
    private final ResolveProcessDefinitionUseCase resolveProcessDefinition;
    private final TaskInstanceRepository taskInstanceRepository;
    private final RuleEvaluationPort ruleEvaluationPort;
    private final ToolStepExecutor toolStepExecutor;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public CompleteTaskUseCase(ProcessInstanceRepository processInstanceRepository,
                                ResolveProcessDefinitionUseCase resolveProcessDefinition,
                                TaskInstanceRepository taskInstanceRepository,
                                RuleEvaluationPort ruleEvaluationPort,
                                ToolStepExecutor toolStepExecutor,
                                TaskActivationService taskActivationService,
                                ApplicationEventPublisher eventPublisher) {
        this.processInstanceRepository = processInstanceRepository;
        this.resolveProcessDefinition = resolveProcessDefinition;
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

        ResolvedProcess definition =
                resolveProcessDefinition.resolveByOrgKeyAndId(orgKey, processInstance.getProcessDefinitionId());
        TaskDefinition taskDef = definition.findTask(taskDefinitionId)
                .orElseThrow(() -> new NotFoundException("Task definition '%s' no longer exists".formatted(taskDefinitionId)))
                .definition();

        // The context this task sees: the instance's initial values, every earlier task's
        // contribution, then whatever the caller supplied with the completion.
        Map<String, Object> inheritedContext = ProcessContext.assemble(
                processInstance, taskInstanceRepository.findByOrgKeyAndProcessInstanceId(orgKey, processInstanceId));
        Map<String, Object> workingContext = new HashMap<>(inheritedContext);
        if (additionalContext != null) {
            workingContext.putAll(additionalContext);
        }

        RuleCheckResult postcondition = ruleEvaluationPort.evaluate(orgKey, taskDef.getPostconditionRuleId(), workingContext);
        if (!postcondition.passed()) {
            return new Result(false, taskInstance, postcondition.detail());
        }

        // Mutates workingContext in place with each step's output mapping, so the contribution below
        // picks those up along with what the caller passed.
        var stepResults = toolStepExecutor.execute(orgKey, taskDef.getSteps(), workingContext);
        taskInstance.setStepResults(stepResults);
        taskInstance.setContextContribution(ProcessContext.contributionOf(inheritedContext, workingContext));
        taskInstance.setStatus(TaskInstanceStatus.COMPLETED);
        taskInstance.setCompletedAt(Instant.now());
        taskInstanceRepository.save(taskInstance);

        // No write to processInstance here, deliberately: that is what used to make two concurrent
        // completions of parallel tasks race on this instance's @Version. See ProcessContext.

        eventPublisher.publishEvent(new TaskCompletedEvent(orgKey, processInstanceId, taskInstance.getId(), taskDefinitionId));

        taskActivationService.activateEligibleTasks(orgKey, definition, processInstanceId, workingContext);

        if (taskActivationService.allTerminal(orgKey, processInstanceId)) {
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
            processInstance.setCompletedAt(Instant.now());
            processInstanceRepository.save(processInstance);
            eventPublisher.publishEvent(new ProcessInstanceCompletedEvent(orgKey, processInstanceId, definition.id()));
        }

        return new Result(true, taskInstance, null);
    }
}
