package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow.ResolvedTask;
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
 *
 * <p>The service reads a {@link ResolvedWorkflow}, so these tests assemble one directly rather than
 * going through {@code ResolveWorkflowUseCase} — the pairing of assignment and task
 * definition is that use case's contract to get right, and repeating it here would only test the
 * fixture.
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
        ResolvedWorkflow workflow = workflowWithSequentialTasks("draft", "review");
        List<TaskInstance> instances = pendingInstances(workflow);
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", workflow, UUID.randomUUID(), Map.of());

        assertThat(statusOf(instances, "draft")).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(statusOf(instances, "review")).isEqualTo(TaskInstanceStatus.PENDING);
    }

    @Test
    void activatesADependentTaskOnceItsDependencyIsCompleted() {
        ResolvedWorkflow workflow = workflowWithSequentialTasks("draft", "review");
        List<TaskInstance> instances = pendingInstances(workflow);
        instanceFor(instances, "draft").setStatus(TaskInstanceStatus.COMPLETED);
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", workflow, UUID.randomUUID(), Map.of());

        assertThat(statusOf(instances, "review")).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    @Test
    void nonParallelSiblingsAtTheSameLevelActivateOneAtATime() {
        ResolvedWorkflow workflow = workflow(
                resolvedTask("task-a", "A", false),
                resolvedTask("task-b", "B", false));

        List<TaskInstance> instances = pendingInstances(workflow);
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", workflow, UUID.randomUUID(), Map.of());

        // exactly one of the two same-level, non-parallel siblings activates
        long activeCount = instances.stream().filter(i -> i.getStatus() == TaskInstanceStatus.ACTIVE).count();
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void parallelSiblingsAtTheSameLevelAllActivateTogether() {
        ResolvedWorkflow workflow = workflow(
                resolvedTask("task-a", "A", true),
                resolvedTask("task-b", "B", true));

        List<TaskInstance> instances = pendingInstances(workflow);
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(any(), any())).thenReturn(instances);

        service.activateEligibleTasks("acme", workflow, UUID.randomUUID(), Map.of());

        assertThat(instances).allMatch(i -> i.getStatus() == TaskInstanceStatus.ACTIVE);
    }

    @Test
    void marksATaskBlockedWhenItsPreconditionFails() {
        TaskDefinition definition = TaskDefinition.builder().id("code").name("Write code")
                .performedByRoles(List.of("developer")).preconditionRuleId("needs-ticket").build();
        ResolvedWorkflow workflow = workflow(new ResolvedTask(assignment("code", false), definition));

        List<TaskInstance> instances = pendingInstances(workflow);
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(any(), any())).thenReturn(instances);
        when(ruleEvaluationPort.evaluate("acme", "needs-ticket", Map.of()))
                .thenReturn(new RuleCheckResult(false, "no ticket linked"));

        service.activateEligibleTasks("acme", workflow, UUID.randomUUID(), Map.of());

        TaskInstance code = instanceFor(instances, "code");
        assertThat(code.getStatus()).isEqualTo(TaskInstanceStatus.BLOCKED);
        assertThat(code.getBlockedReason()).isEqualTo("no ticket linked");
    }

    @Test
    void allTerminalIsTrueOnlyWhenEveryTaskIsCompletedOrSkipped() {
        List<TaskInstance> instances = List.of(
                TaskInstance.builder().status(TaskInstanceStatus.COMPLETED).build(),
                TaskInstance.builder().status(TaskInstanceStatus.SKIPPED).build());
        UUID workflowInstanceId = UUID.randomUUID();
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId("acme", workflowInstanceId)).thenReturn(instances);

        assertThat(service.allTerminal("acme", workflowInstanceId)).isTrue();
    }

    // ---------------------------------------------------------------- join type

    /**
     * ALL is the default and the historical behaviour: a task with two dependencies waits for both.
     * The interesting assertion is the middle state — one dependency done is not enough.
     */
    @Test
    void allJoinWaitsForEveryDependency() {
        ResolvedWorkflow workflow = workflowWithJoin(JoinType.ALL);
        List<TaskInstance> instances = pendingInstances(workflow);
        UUID workflowInstanceId = UUID.randomUUID();
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId("acme", workflowInstanceId)).thenReturn(instances);

        instanceFor(instances, "left").setStatus(TaskInstanceStatus.COMPLETED);
        service.activateEligibleTasks("acme", workflow, workflowInstanceId, Map.of());
        assertThat(statusOf(instances, "join")).isEqualTo(TaskInstanceStatus.PENDING);

        instanceFor(instances, "right").setStatus(TaskInstanceStatus.SKIPPED);
        service.activateEligibleTasks("acme", workflow, workflowInstanceId, Map.of());
        assertThat(statusOf(instances, "join")).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    /** ANY activates on the first dependency to reach a terminal status; the other never has to. */
    @Test
    void anyJoinActivatesOnTheFirstFinishedDependency() {
        ResolvedWorkflow workflow = workflowWithJoin(JoinType.ANY);
        List<TaskInstance> instances = pendingInstances(workflow);
        UUID workflowInstanceId = UUID.randomUUID();
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId("acme", workflowInstanceId)).thenReturn(instances);

        service.activateEligibleTasks("acme", workflow, workflowInstanceId, Map.of());
        assertThat(statusOf(instances, "join")).isEqualTo(TaskInstanceStatus.PENDING);

        instanceFor(instances, "left").setStatus(TaskInstanceStatus.COMPLETED);
        service.activateEligibleTasks("acme", workflow, workflowInstanceId, Map.of());
        assertThat(statusOf(instances, "join")).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(statusOf(instances, "right")).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    /**
     * A null joinType is read as ALL rather than dropping through to some other branch — an
     * already-persisted task use predates the field.
     */
    @Test
    void anAbsentJoinTypeBehavesAsAll() {
        ResolvedWorkflow workflow = workflowWithJoin(null);
        List<TaskInstance> instances = pendingInstances(workflow);
        UUID workflowInstanceId = UUID.randomUUID();
        when(taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId("acme", workflowInstanceId)).thenReturn(instances);

        instanceFor(instances, "left").setStatus(TaskInstanceStatus.COMPLETED);
        service.activateEligibleTasks("acme", workflow, workflowInstanceId, Map.of());

        assertThat(statusOf(instances, "join")).isEqualTo(TaskInstanceStatus.PENDING);
    }

    // ---------------------------------------------------------------- fixtures

    private ResolvedWorkflow workflowWithSequentialTasks(String firstId, String secondId) {
        return workflow(
                resolvedTask(firstId, firstId, false),
                new ResolvedTask(
                        TaskUse.builder().taskDefinitionId(secondId).performedBy("developer")
                                .dependsOn(List.of(firstId)).build(),
                        TaskDefinition.builder().id(secondId).name(secondId)
                                .performedByRoles(List.of("developer")).build()));
    }

    /**
     * Two independent tasks and one that joins them, so the two join types differ observably. Both
     * branches are {@code parallel} — otherwise the sequential-sibling rule, not the join type,
     * would be what holds the second branch back.
     */
    private ResolvedWorkflow workflowWithJoin(JoinType joinType) {
        return workflow(
                resolvedTask("left", "left", true),
                resolvedTask("right", "right", true),
                new ResolvedTask(
                        TaskUse.builder().taskDefinitionId("join").performedBy("developer")
                                .dependsOn(List.of("left", "right")).joinType(joinType).build(),
                        TaskDefinition.builder().id("join").name("join")
                                .performedByRoles(List.of("developer")).build()));
    }

    private ResolvedWorkflow workflow(ResolvedTask... tasks) {
        Workflow definition = Workflow.builder().orgKey("acme").id("delivery")
                .roles(List.of(RoleUse.builder().roleDefinitionId("developer").build()))
                .tasks(List.of(tasks).stream().map(ResolvedTask::assignment).toList())
                .build();
        RoleDefinition developer = RoleDefinition.builder().orgKey("acme").id("developer").name("Developer").build();
        return new ResolvedWorkflow(definition, List.of(developer), List.of(), List.of(tasks));
    }

    private ResolvedTask resolvedTask(String id, String name, boolean parallel) {
        return new ResolvedTask(assignment(id, parallel),
                TaskDefinition.builder().id(id).name(name).performedByRoles(List.of("developer")).build());
    }

    private TaskUse assignment(String taskDefinitionId, boolean parallel) {
        return TaskUse.builder().taskDefinitionId(taskDefinitionId).performedBy("developer")
                .dependsOn(List.of()).parallel(parallel).build();
    }

    private List<TaskInstance> pendingInstances(ResolvedWorkflow workflow) {
        return workflow.tasks().stream()
                .map(t -> TaskInstance.builder()
                        .id(UUID.randomUUID())
                        .orgKey("acme")
                        .taskDefinitionId(t.id())
                        .name(t.definition().getName())
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
