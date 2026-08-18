package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link TaskActivationService} against an in-memory fake repository, since its
 * decision logic (dependency satisfaction, sequential-sibling ordering, precondition evaluation)
 * is exactly the part worth unit-testing in isolation from Spring/JPA.
 */
class TaskActivationServiceTest {

    private final List<TaskInstance> savedInstances = new ArrayList<>();
    private TaskInstanceRepository taskInstanceRepository;
    private RuleEvaluationPort ruleEvaluationPort;
    private TaskActivationService service;

    @BeforeEach
    void setUp() {
        taskInstanceRepository = mock(TaskInstanceRepository.class);
        ruleEvaluationPort = mock(RuleEvaluationPort.class);
        when(ruleEvaluationPort.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskInstanceRepository.save(any())).thenAnswer(invocation -> {
            TaskInstance saved = invocation.getArgument(0);
            savedInstances.add(saved);
            return saved;
        });
        service = new TaskActivationService(taskInstanceRepository, ruleEvaluationPort, mock(ApplicationEventPublisher.class));
    }

    @Test
    void activatesTasksWithNoDependenciesAndLeavesDependentTasksPending() {
        ProcessDefinition process = processWithSequentialTasks("draft", "review");
        List<TaskInstance> instances = pendingInstances(process);
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", process, UUID.randomUUID(), Map.of());

        assertThat(statusOf(instances, "draft")).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(statusOf(instances, "review")).isEqualTo(TaskInstanceStatus.PENDING);
    }

    @Test
    void activatesADependentTaskOnceItsDependencyIsCompleted() {
        ProcessDefinition process = processWithSequentialTasks("draft", "review");
        List<TaskInstance> instances = pendingInstances(process);
        instanceFor(instances, "draft").setStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", process, UUID.randomUUID(), Map.of());

        assertThat(statusOf(instances, "review")).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    @Test
    void nonParallelSiblingsAtTheSameLevelActivateOneAtATime() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("task-a").name("A").performedBy("developer").build());
        process.addTask(TaskDefinition.builder().id("task-b").name("B").performedBy("developer").build());

        List<TaskInstance> instances = pendingInstances(process);
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", process, UUID.randomUUID(), Map.of());

        // exactly one of the two same-level, non-parallel siblings activates
        long activeCount = instances.stream().filter(i -> i.getStatus() == TaskInstanceStatus.ACTIVE).count();
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void parallelSiblingsAtTheSameLevelAllActivateTogether() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("task-a").name("A").performedBy("developer").parallel(true).build());
        process.addTask(TaskDefinition.builder().id("task-b").name("B").performedBy("developer").parallel(true).build());

        List<TaskInstance> instances = pendingInstances(process);
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", process, UUID.randomUUID(), Map.of());

        assertThat(instances).allMatch(i -> i.getStatus() == TaskInstanceStatus.ACTIVE);
    }

    @Test
    void marksATaskBlockedWhenItsPreconditionFails() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("code").name("Write code").performedBy("developer")
                .preconditionRuleId("needs-ticket").build());

        List<TaskInstance> instances = pendingInstances(process);
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId(any(), any())).thenReturn(instances);
        when(ruleEvaluationPort.evaluate("acme", "needs-ticket", Map.of()))
                .thenReturn(new RuleCheckResult(false, "no ticket linked"));

        service.activateEligibleTasks("acme", process, UUID.randomUUID(), Map.of());

        TaskInstance code = instanceFor(instances, "code");
        assertThat(code.getStatus()).isEqualTo(TaskInstanceStatus.BLOCKED);
        assertThat(code.getBlockedReason()).isEqualTo("no ticket linked");
    }

    @Test
    void allTerminalIsTrueOnlyWhenEveryTaskIsCompletedOrSkipped() {
        List<TaskInstance> instances = List.of(
                TaskInstance.builder().status(TaskInstanceStatus.COMPLETED).build(),
                TaskInstance.builder().status(TaskInstanceStatus.SKIPPED).build());
        UUID processInstanceId = UUID.randomUUID();
        when(taskInstanceRepository.findByOrgKeyAndProcessInstanceId("acme", processInstanceId)).thenReturn(instances);

        assertThat(service.allTerminal("acme", processInstanceId)).isTrue();
    }

    private ProcessDefinition processWithSequentialTasks(String firstId, String secondId) {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id(firstId).name(firstId).performedBy("developer").build());
        process.addTask(TaskDefinition.builder().id(secondId).name(secondId).performedBy("developer")
                .dependsOn(List.of(firstId)).build());
        return process;
    }

    private List<TaskInstance> pendingInstances(ProcessDefinition process) {
        return process.getTasks().stream()
                .map(t -> TaskInstance.builder()
                        .id(UUID.randomUUID())
                        .orgKey("acme")
                        .taskDefinitionId(t.getId())
                        .name(t.getName())
                        .status(TaskInstanceStatus.PENDING)
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private TaskInstance instanceFor(List<TaskInstance> instances, String taskDefinitionId) {
        return instances.stream().filter(i -> i.getTaskDefinitionId().equals(taskDefinitionId)).findFirst().orElseThrow();
    }

    private TaskInstanceStatus statusOf(List<TaskInstance> instances, String taskDefinitionId) {
        return instanceFor(instances, taskDefinitionId).getStatus();
    }
}
