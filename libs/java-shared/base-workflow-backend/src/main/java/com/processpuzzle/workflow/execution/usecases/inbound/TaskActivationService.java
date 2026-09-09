package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow.ResolvedTask;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.events.TaskActivatedEvent;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The workflow engine's core: decides which PENDING/BLOCKED tasks of a running workflow instance
 * are eligible to become ACTIVE right now, and evaluates each one's precondition rule. Called
 * after every state-changing event within a workflow instance (start, task completion, task skip)
 * so the workflow keeps advancing on its own.
 *
 * <p>It works against a {@link ResolvedWorkflow} rather than a {@code Workflow} because the wiring it
 * reads — {@code dependsOn}, {@code joinType}, {@code parallel} — lives on the workflow's
 * {@code TaskUse}, while the precondition rule belongs to the shared task definition; the resolved
 * view is where the two are already paired up.
 *
 * <p><b>Ordering within a dependency level:</b> a task is only a <em>candidate</em> once every
 * task in its {@code dependsOn} is COMPLETED or SKIPPED. Among candidates that share the exact
 * same {@code dependsOn} set (i.e. the same "level" of the graph), {@code parallel == false} (the
 * default) tasks run one at a time in workflow-definition order: a non-parallel candidate only
 * attempts activation once no earlier sibling at that level is still ACTIVE or BLOCKED. {@code parallel == true} tasks skip that check and may all activate together. This is
 * a reasonable, defensible reading of the contract rather than a literal spec requirement — the
 * API description only says parallel "can run concurrently with its siblings that share the same
 * dependsOn", it doesn't fully specify non-parallel ordering, so this fills the gap the way SPEM's
 * own informal-sequencing intent suggests.
 */
@Service
public class TaskActivationService {

    private final TaskInstanceRepository taskInstanceRepository;
    private final RuleEvaluationPort ruleEvaluationPort;
    private final ApplicationEventPublisher eventPublisher;

    public TaskActivationService(TaskInstanceRepository taskInstanceRepository,
                                  RuleEvaluationPort ruleEvaluationPort,
                                  ApplicationEventPublisher eventPublisher) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.ruleEvaluationPort = ruleEvaluationPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Re-evaluates every non-terminal task instance of {@code workflowInstanceId} and activates
     * the ones now eligible. Idempotent: calling it repeatedly with no state change is a no-op.
     */
    public void activateEligibleTasks(String orgKey, ResolvedWorkflow workflow,
                                       java.util.UUID workflowInstanceId, Map<String, Object> context) {
        List<TaskInstance> instances = taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
        Map<String, TaskInstance> byDefinitionId = instances.stream()
                .collect(Collectors.toMap(TaskInstance::getTaskDefinitionId, t -> t));

        Set<TaskInstanceStatus> terminal = Set.of(TaskInstanceStatus.COMPLETED, TaskInstanceStatus.SKIPPED);

        for (ResolvedTask task : workflow.tasks()) {
            activateTaskIfEligible(orgKey, task, workflow, workflowInstanceId, context, byDefinitionId, terminal);
        }
    }

    private void activateTaskIfEligible(String orgKey, ResolvedTask task, ResolvedWorkflow workflow,
                                        java.util.UUID workflowInstanceId, Map<String, Object> context,
                                        Map<String, TaskInstance> byDefinitionId, Set<TaskInstanceStatus> terminal) {
        TaskInstance instance = byDefinitionId.get(task.id());
        if (instance == null || (instance.getStatus() != TaskInstanceStatus.PENDING
                && instance.getStatus() != TaskInstanceStatus.BLOCKED)) {
            return;
        }

        if (!areDependenciesSatisfied(task, byDefinitionId, terminal)) {
            return;
        }

        if (!task.parallel() && hasActiveSiblingAtSameLevel(task, workflow, byDefinitionId)) {
            return;
        }

        RuleCheckResult check = ruleEvaluationPort.evaluate(orgKey, task.definition().getPreconditionRuleId(), context);
        if (check.passed()) {
            instance.setStatus(TaskInstanceStatus.ACTIVE);
            instance.setActivatedAt(Instant.now());
            instance.setBlockedReason(null);
            taskInstanceRepository.save(instance);
            eventPublisher.publishEvent(new TaskActivatedEvent(orgKey, workflowInstanceId, instance.getId(), task.id()));
        } else {
            instance.setStatus(TaskInstanceStatus.BLOCKED);
            instance.setBlockedReason(check.detail());
            taskInstanceRepository.save(instance);
        }
    }

    /**
     * ALL (the default) waits for every named task, ANY for the first of them. An empty
     * {@code dependsOn} is satisfied under either, which is what makes a task with no dependencies
     * eligible from workflow start: {@code allMatch} over nothing is true, and the ANY branch checks
     * for emptiness explicitly rather than letting {@code anyMatch} return false.
     */
    private boolean areDependenciesSatisfied(ResolvedTask task, Map<String, TaskInstance> byDefinitionId,
                                             Set<TaskInstanceStatus> terminal) {
        List<String> dependsOn = task.dependsOn();
        if (dependsOn.isEmpty()) {
            return true;
        }
        Predicate<String> isTerminal = depId -> {
            TaskInstance dep = byDefinitionId.get(depId);
            return dep != null && terminal.contains(dep.getStatus());
        };
        return task.joinType() == JoinType.ANY
                ? dependsOn.stream().anyMatch(isTerminal)
                : dependsOn.stream().allMatch(isTerminal);
    }

    private boolean hasActiveSiblingAtSameLevel(ResolvedTask task, ResolvedWorkflow workflow,
                                                 Map<String, TaskInstance> byDefinitionId) {
        return workflow.tasks().stream()
                .filter(sibling -> !sibling.id().equals(task.id()))
                .filter(sibling -> sibling.dependsOn().equals(task.dependsOn()))
                .filter(sibling -> !sibling.parallel())
                .map(sibling -> byDefinitionId.get(sibling.id()))
                .anyMatch(siblingInstance -> siblingInstance != null
                        && (siblingInstance.getStatus() == TaskInstanceStatus.ACTIVE
                            || siblingInstance.getStatus() == TaskInstanceStatus.BLOCKED));
    }

    public boolean allTerminal(String orgKey, java.util.UUID workflowInstanceId) {
        return taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId).stream()
                .allMatch(t -> t.getStatus() == TaskInstanceStatus.COMPLETED || t.getStatus() == TaskInstanceStatus.SKIPPED);
    }
}
