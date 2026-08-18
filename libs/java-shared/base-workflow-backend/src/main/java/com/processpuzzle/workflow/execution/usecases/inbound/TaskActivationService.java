package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.events.TaskActivatedEvent;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The process engine's core: decides which PENDING/BLOCKED tasks of a running process instance
 * are eligible to become ACTIVE right now, and evaluates each one's precondition rule. Called
 * after every state-changing event within a process instance (start, task completion, task skip)
 * so the process keeps advancing on its own.
 *
 * <p><b>Ordering within a dependency level:</b> a task is only a <em>candidate</em> once every
 * task in its {@code dependsOn} is COMPLETED or SKIPPED. Among candidates that share the exact
 * same {@code dependsOn} set (i.e. the same "level" of the graph), {@code TaskDefinition.parallel
 * == false} (the default) tasks run one at a time in process-definition order: a non-parallel
 * candidate only attempts activation once no earlier sibling at that level is still ACTIVE or
 * BLOCKED. {@code parallel == true} tasks skip that check and may all activate together. This is
 * a reasonable, defensible reading of the contract rather than a literal spec requirement — the
 * API description only says parallel "can run concurrently with its siblings that share the same
 * dependsOn", it doesn't fully specify non-parallel ordering, so this fills the gap the way SPEM's
 * own informal-sequencing intent suggests.
 */
@Component
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
     * Re-evaluates every non-terminal task instance of {@code processInstanceId} and activates
     * the ones now eligible. Idempotent: calling it repeatedly with no state change is a no-op.
     */
    public void activateEligibleTasks(String orgKey, ProcessDefinition process,
                                       java.util.UUID processInstanceId, Map<String, Object> context) {
        List<TaskInstance> instances = taskInstanceRepository.findByOrgKeyAndProcessInstanceId(orgKey, processInstanceId);
        Map<String, TaskInstance> byDefinitionId = instances.stream()
                .collect(Collectors.toMap(TaskInstance::getTaskDefinitionId, t -> t));

        Set<TaskInstanceStatus> terminal = Set.of(TaskInstanceStatus.COMPLETED, TaskInstanceStatus.SKIPPED);

        for (TaskDefinition taskDef : process.getTasks()) {
            TaskInstance instance = byDefinitionId.get(taskDef.getId());
            if (instance == null || instance.getStatus() != TaskInstanceStatus.PENDING
                    && instance.getStatus() != TaskInstanceStatus.BLOCKED) {
                continue; // ACTIVE/COMPLETED/SKIPPED — nothing to do
            }

            boolean dependenciesSatisfied = taskDef.getDependsOn().stream()
                    .allMatch(depId -> {
                        TaskInstance dep = byDefinitionId.get(depId);
                        return dep != null && terminal.contains(dep.getStatus());
                    });
            if (!dependenciesSatisfied) {
                continue;
            }

            if (!taskDef.isParallel() && hasActiveSiblingAtSameLevel(taskDef, process, byDefinitionId)) {
                continue;
            }

            RuleCheckResult check = ruleEvaluationPort.evaluate(orgKey, taskDef.getPreconditionRuleId(), context);
            if (check.passed()) {
                instance.setStatus(TaskInstanceStatus.ACTIVE);
                instance.setActivatedAt(Instant.now());
                instance.setBlockedReason(null);
                taskInstanceRepository.save(instance);
                eventPublisher.publishEvent(new TaskActivatedEvent(orgKey, processInstanceId, instance.getId(), taskDef.getId()));
            } else {
                instance.setStatus(TaskInstanceStatus.BLOCKED);
                instance.setBlockedReason(check.detail());
                taskInstanceRepository.save(instance);
            }
        }
    }

    private boolean hasActiveSiblingAtSameLevel(TaskDefinition taskDef, ProcessDefinition process,
                                                 Map<String, TaskInstance> byDefinitionId) {
        return process.getTasks().stream()
                .filter(sibling -> !sibling.getId().equals(taskDef.getId()))
                .filter(sibling -> sibling.getDependsOn().equals(taskDef.getDependsOn()))
                .filter(sibling -> !sibling.isParallel())
                .map(sibling -> byDefinitionId.get(sibling.getId()))
                .anyMatch(siblingInstance -> siblingInstance != null
                        && (siblingInstance.getStatus() == TaskInstanceStatus.ACTIVE
                            || siblingInstance.getStatus() == TaskInstanceStatus.BLOCKED));
    }

    public boolean allTerminal(String orgKey, java.util.UUID processInstanceId) {
        return taskInstanceRepository.findByOrgKeyAndProcessInstanceId(orgKey, processInstanceId).stream()
                .allMatch(t -> t.getStatus() == TaskInstanceStatus.COMPLETED || t.getStatus() == TaskInstanceStatus.SKIPPED);
    }
}
