package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StepResult;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.outbound.RoleMembershipPort;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationPort;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;
import java.time.Instant;
import org.mockito.ArgumentCaptor;

class WorkflowExecutionUseCasesTest {

    private static final String ORG = "org-1";

    private ResolveWorkflowUseCase resolveWorkflow;
    private WorkflowInstanceRepository procInstRepo;
    private TaskInstanceRepository taskInstRepo;
    private ArtifactInstanceRepository wpInstRepo;
    private TaskActivationService taskActivationService;
    private ApplicationEventPublisher eventPublisher;
    private RuleEvaluationPort ruleEvaluationPort;
    private ToolDefinitionRepository toolDefRepo;
    private ToolInvocationPort toolInvocationPort;
    private RoleMembershipPort roleMembershipPort;

    @BeforeEach
    void setUp() {
        resolveWorkflow = mock(ResolveWorkflowUseCase.class);
        procInstRepo = mock(WorkflowInstanceRepository.class);
        taskInstRepo = mock(TaskInstanceRepository.class);
        wpInstRepo = mock(ArtifactInstanceRepository.class);
        taskActivationService = mock(TaskActivationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ruleEvaluationPort = mock(RuleEvaluationPort.class);
        toolDefRepo = mock(ToolDefinitionRepository.class);
        toolInvocationPort = mock(ToolInvocationPort.class);
        roleMembershipPort = mock(RoleMembershipPort.class);
    }

    @Test
    void startWorkflowInstanceUseCase_startsWorkflowWithTasksAndArtifacts() {
        StartWorkflowInstanceUseCase useCase = new StartWorkflowInstanceUseCase(
                resolveWorkflow, procInstRepo, taskInstRepo, wpInstRepo, taskActivationService, eventPublisher);

        ArtifactDefinition wpDef = ArtifactDefinition.builder().id("wp-1").name("Doc")
                .artifactType(ArtifactType.DOCUMENT).stateMachineId("sm-1").build();
        TaskDefinition taskDef = TaskDefinition.builder().id("t-1").name("Task 1").build();
        ResolvedWorkflow procDef = resolved("proc-1", "Proc 1", List.of(wpDef), taskDef);

        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1")).thenReturn(procDef);
        when(procInstRepo.save(any(WorkflowInstance.class))).thenAnswer(inv -> {
            WorkflowInstance pi = inv.getArgument(0);
            pi.setId(UUID.randomUUID());
            return pi;
        });
        when(wpInstRepo.save(any(ArtifactInstance.class))).thenAnswer(inv -> {
            ArtifactInstance wpi = inv.getArgument(0);
            wpi.setId(UUID.randomUUID());
            return wpi;
        });

        WorkflowInstance started = useCase.start(ORG, "proc-1", "entity-1", Map.of("initKey", "initVal"));
        assertThat(started).isNotNull();
        assertThat(started.getStatus()).isEqualTo(WorkflowInstanceStatus.ACTIVE);
        assertThat(started.getInitialContext()).containsEntry("initKey", "initVal");

        verify(taskActivationService).activateEligibleTasks(eq(ORG), eq(procDef), eq(started.getId()), any());

        // Start unknown workflow — the resolver is what refuses, strictly
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "unknown"))
                .thenThrow(new NotFoundException("No workflow with id 'unknown'"));
        assertThatThrownBy(() -> useCase.start(ORG, "unknown", "entity-1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelWorkflowInstanceUseCase_successAndConflict() {
        CancelWorkflowInstanceUseCase useCase = new CancelWorkflowInstanceUseCase(procInstRepo, eventPublisher);
        UUID instanceId = UUID.randomUUID();
        WorkflowInstance active = WorkflowInstance.builder().id(instanceId).orgKey(ORG).status(WorkflowInstanceStatus.ACTIVE).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(active));

        useCase.cancel(ORG, instanceId, "User requested");
        assertThat(active.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
        verify(procInstRepo).save(active);

        // Cancel already cancelled / completed throws ConflictException
        WorkflowInstance cancelled = WorkflowInstance.builder().id(instanceId).orgKey(ORG).status(WorkflowInstanceStatus.CANCELLED).build();
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> useCase.cancel(ORG, instanceId, "Again"))
                .isInstanceOf(ConflictException.class);

        // Cancel unknown
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.cancel(ORG, instanceId, "Again"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAndFindAllWorkflowInstancesUseCase() {
        FindWorkflowInstanceUseCase findUseCase = new FindWorkflowInstanceUseCase(procInstRepo);
        FindAllWorkflowInstancesUseCase findAllUseCase = new FindAllWorkflowInstancesUseCase(procInstRepo);

        UUID instanceId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(procInstRepo.findByOrgKeyAndId(ORG, unknownId)).thenReturn(Optional.empty());

        assertThat(findUseCase.findByOrgKeyAndId(ORG, instanceId)).isEqualTo(pi);
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, unknownId))
                .isInstanceOf(NotFoundException.class);

        when(procInstRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pi)));

        FindAllWorkflowInstancesUseCase.Query query = new FindAllWorkflowInstancesUseCase.Query(
                ORG, "proc-1", WorkflowInstanceStatus.ACTIVE, "entity-1", "status == 'ACTIVE'", "startedAt,desc", 0, 10);
        Page<WorkflowInstance> result = findAllUseCase.findAll(query);
        assertThat(result.getContent()).containsExactly(pi);
    }

    @Test
    void assignTaskUseCase_successAndRoleValidation() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RoleMembershipPort> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(roleMembershipPort);

        AssignTaskUseCase useCase = new AssignTaskUseCase(procInstRepo, resolveWorkflow, taskInstRepo, provider);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1").build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();
        RoleDefinition role = RoleDefinition.builder().id("dev").name("Dev").entityRoleId("role-dev").build();
        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1")
                .performedByRoles(List.of("dev")).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolvedWithRole("proc-1", role, "dev", taskDef));
        when(taskInstRepo.save(ti)).thenReturn(ti);

        // Role membership ok
        when(roleMembershipPort.isMember(ORG, "user-1", "role-dev")).thenReturn(true);
        TaskInstance assigned = useCase.assign(ORG, instanceId, "task-1", "user-1");
        assertThat(assigned.getAssignedTo()).isEqualTo("user-1");

        // PerformedBy is null -> assigns without role check
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolvedWithRole("proc-1", role, null, taskDef));
        TaskInstance unassignedResult = useCase.assign(ORG, instanceId, "task-1", "user-any");
        assertThat(unassignedResult.getAssignedTo()).isEqualTo("user-any");
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolvedWithRole("proc-1", role, "dev", taskDef));

        // Role has no entityRoleId -> assigns without role check
        role.setEntityRoleId(null);
        TaskInstance noEntityRoleResult = useCase.assign(ORG, instanceId, "task-1", "user-any");
        assertThat(noEntityRoleResult.getAssignedTo()).isEqualTo("user-any");
        role.setEntityRoleId("role-dev");

        // Role membership fails
        when(roleMembershipPort.isMember(ORG, "user-2", "role-dev")).thenReturn(false);
        assertThatThrownBy(() -> useCase.assign(ORG, instanceId, "task-1", "user-2"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not hold role");

        // Non-ACTIVE task assignment conflict
        ti.setStatus(TaskInstanceStatus.COMPLETED);
        assertThatThrownBy(() -> useCase.assign(ORG, instanceId, "task-1", "user-1"))
                .isInstanceOf(ConflictException.class);

        // Not found cases
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.assign(ORG, instanceId, "task-1", "user-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void completeTaskUseCase_successAndPostconditionFailure() {
        ToolStepExecutor stepExecutor = new ToolStepExecutor(toolDefRepo, toolInvocationPort);
        CompleteTaskUseCase useCase = new CompleteTaskUseCase(
                procInstRepo, resolveWorkflow, taskInstRepo, ruleEvaluationPort, stepExecutor,
                taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(new HashMap<>(Map.of("a", "1"))).build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();
        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1").postconditionRuleId("rule-post").steps(List.of()).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), taskDef));

        // Postcondition failed
        when(ruleEvaluationPort.evaluate(eq(ORG), eq("rule-post"), any()))
                .thenReturn(new RuleCheckResult(false, "Postcondition violation"));

        CompleteTaskUseCase.Result failedResult = useCase.complete(ORG, instanceId, "task-1", Map.of("extra", "2"));
        assertThat(failedResult.accepted()).isFalse();
        assertThat(failedResult.postconditionDetail()).isEqualTo("Postcondition violation");
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);

        // Postcondition passed, all terminal -> completes workflow instance
        when(ruleEvaluationPort.evaluate(eq(ORG), eq("rule-post"), any()))
                .thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(true);

        CompleteTaskUseCase.Result successResult = useCase.complete(ORG, instanceId, "task-1", Map.of("extra", "2"));
        assertThat(successResult.accepted()).isTrue();
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
        assertThat(pi.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);

        // Steps execution during completion
        StepDefinition step = StepDefinition.builder().id("s1").build();
        TaskDefinition taskWithSteps = TaskDefinition.builder().id("task-1").name("Task 1").postconditionRuleId("rule-post").steps(List.of(step)).build();
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), taskWithSteps));
        ti.setStatus(TaskInstanceStatus.ACTIVE);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(false);

        CompleteTaskUseCase.Result stepsResult = useCase.complete(ORG, instanceId, "task-1", null);
        assertThat(stepsResult.accepted()).isTrue();
        assertThat(ti.getStepResults()).hasSize(1);

        // Complete non-active task -> conflict
        ti.setStatus(TaskInstanceStatus.COMPLETED);
        assertThatThrownBy(() -> useCase.complete(ORG, instanceId, "task-1", null))
                .isInstanceOf(ConflictException.class);

        // Not found cases
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.complete(ORG, instanceId, "task-1", null))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * The regression guard for the reason the context stopped being accumulated on the workflow
     * instance. A mid-workflow completion must write the task row and nothing else: while every
     * completion also wrote {@code WorkflowInstance}, its {@code @Version} serialized two users
     * completing two {@code parallel} tasks, so one of them lost the race and {@code parallel} did
     * not work concurrently.
     *
     * <p>Asserting on "does not save" rather than on a simulated collision is deliberate: the
     * collision needs two real transactions, which a unit test cannot stage, whereas the write that
     * causes it is exactly one interaction away.
     */
    @Test
    void completeTaskUseCase_doesNotWriteTheWorkflowInstanceMidWorkflow() {
        ToolStepExecutor stepExecutor = new ToolStepExecutor(toolDefRepo, toolInvocationPort);
        CompleteTaskUseCase useCase = new CompleteTaskUseCase(
                procInstRepo, resolveWorkflow, taskInstRepo, ruleEvaluationPort, stepExecutor,
                taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(new HashMap<>(Map.of("orderId", "o-1"))).build();
        TaskInstance left = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("left").status(TaskInstanceStatus.ACTIVE).build();
        TaskInstance right = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("right").status(TaskInstanceStatus.ACTIVE).build();
        TaskDefinition leftDef = TaskDefinition.builder().id("left").name("Left").steps(List.of()).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "left"))
                .thenReturn(Optional.of(left));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceId(ORG, instanceId)).thenReturn(List.of(left, right));
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), leftDef));
        when(ruleEvaluationPort.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        // "right" is still ACTIVE, so this is not the closing completion.
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(false);

        CompleteTaskUseCase.Result result = useCase.complete(ORG, instanceId, "left", Map.of("reviewedBy", "clerk"));

        assertThat(result.accepted()).isTrue();
        verify(taskInstRepo).save(left);
        verify(procInstRepo, never()).save(any());

        // What the completion added lives on the task, and folds back into the same context the task
        // was working with.
        assertThat(left.getContextContribution()).containsExactlyEntriesOf(Map.of("reviewedBy", "clerk"));
        assertThat(pi.getInitialContext()).doesNotContainKey("reviewedBy");
        assertThat(WorkflowContext.assemble(pi, List.of(left, right)))
                .containsEntry("orderId", "o-1")
                .containsEntry("reviewedBy", "clerk");
    }

    /** The closing completion is the one write, and only one completion can be it. */
    @Test
    void completeTaskUseCase_writesTheWorkflowInstanceOnlyToCloseItOut() {
        ToolStepExecutor stepExecutor = new ToolStepExecutor(toolDefRepo, toolInvocationPort);
        CompleteTaskUseCase useCase = new CompleteTaskUseCase(
                procInstRepo, resolveWorkflow, taskInstRepo, ruleEvaluationPort, stepExecutor,
                taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(new HashMap<>()).build();
        TaskInstance only = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("only").status(TaskInstanceStatus.ACTIVE).build();
        TaskDefinition onlyDef = TaskDefinition.builder().id("only").name("Only").steps(List.of()).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "only"))
                .thenReturn(Optional.of(only));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceId(ORG, instanceId)).thenReturn(List.of(only));
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), onlyDef));
        when(ruleEvaluationPort.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(true);

        useCase.complete(ORG, instanceId, "only", null);

        assertThat(pi.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
        verify(procInstRepo, times(1)).save(pi);
    }

    /**
     * An earlier task's contribution has to reach a later task's postcondition and tool inputs — that
     * is the whole point of a shared context, and the fold is what preserves it now that the
     * instance no longer carries one.
     */
    @Test
    void completeTaskUseCase_inheritsEarlierTasksContributions() {
        ToolStepExecutor stepExecutor = new ToolStepExecutor(toolDefRepo, toolInvocationPort);
        CompleteTaskUseCase useCase = new CompleteTaskUseCase(
                procInstRepo, resolveWorkflow, taskInstRepo, ruleEvaluationPort, stepExecutor,
                taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(new HashMap<>(Map.of("orderId", "o-1"))).build();
        TaskInstance earlier = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("review").status(TaskInstanceStatus.COMPLETED)
                .completedAt(Instant.ofEpochMilli(100))
                .contextContribution(new HashMap<>(Map.of("reviewedBy", "clerk"))).build();
        TaskInstance later = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("approve").status(TaskInstanceStatus.ACTIVE).build();
        TaskDefinition laterDef = TaskDefinition.builder().id("approve").name("Approve")
                .postconditionRuleId("rule-post").steps(List.of()).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "approve"))
                .thenReturn(Optional.of(later));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceId(ORG, instanceId)).thenReturn(List.of(earlier, later));
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), laterDef));
        when(ruleEvaluationPort.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(false);

        useCase.complete(ORG, instanceId, "approve", Map.of("approvedBy", "manager"));

        // The postcondition saw the earlier contribution...
        ArgumentCaptor<Map<String, Object>> evaluated = ArgumentCaptor.captor();
        verify(ruleEvaluationPort).evaluate(eq(ORG), eq("rule-post"), evaluated.capture());
        assertThat(evaluated.getValue())
                .containsEntry("orderId", "o-1")
                .containsEntry("reviewedBy", "clerk")
                .containsEntry("approvedBy", "manager");

        // ...and this task recorded only its own delta, not the inherited values.
        assertThat(later.getContextContribution()).containsExactlyEntriesOf(Map.of("approvedBy", "manager"));
    }

    @Test
    void skipTaskUseCase_successAndConflict() {
        SkipTaskUseCase useCase = new SkipTaskUseCase(
                procInstRepo, resolveWorkflow, taskInstRepo, taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("proc-1")
                .status(WorkflowInstanceStatus.ACTIVE).initialContext(new HashMap<>()).build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();

        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1").build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(taskInstRepo.save(ti)).thenReturn(ti);
        when(resolveWorkflow.resolveByOrgKeyAndId(ORG, "proc-1"))
                .thenReturn(resolved("proc-1", "Proc 1", List.of(), taskDef));
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(true);

        TaskInstance skipped = useCase.skip(ORG, instanceId, "task-1", "Not needed");
        assertThat(skipped.getStatus()).isEqualTo(TaskInstanceStatus.SKIPPED);
        assertThat(pi.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);

        // Skip from PENDING when not all terminal
        ti.setStatus(TaskInstanceStatus.PENDING);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(false);
        useCase.skip(ORG, instanceId, "task-1", "Skip pending");
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.SKIPPED);

        // Skip from BLOCKED
        ti.setStatus(TaskInstanceStatus.BLOCKED);
        useCase.skip(ORG, instanceId, "task-1", "Skip blocked");
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.SKIPPED);

        // Already skipped throws ConflictException
        assertThatThrownBy(() -> useCase.skip(ORG, instanceId, "task-1", "Skip again"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already SKIPPED");

        // Not found
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.skip(ORG, instanceId, "task-1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAndListTaskAndArtifactUseCases() {
        FindTaskInstanceUseCase findTask = new FindTaskInstanceUseCase(taskInstRepo);
        ListTaskInstancesUseCase listTasks = new ListTaskInstancesUseCase(taskInstRepo);
        FindArtifactInstanceUseCase findWp = new FindArtifactInstanceUseCase(wpInstRepo);
        ListArtifactInstancesUseCase listWps = new ListArtifactInstancesUseCase(wpInstRepo);

        UUID instanceId = UUID.randomUUID();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId).taskDefinitionId("t1").build();
        ArtifactInstance wpi = ArtifactInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId).artifactDefinitionId("wp1").build();

        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "t1")).thenReturn(Optional.of(ti));
        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceId(ORG, instanceId)).thenReturn(List.of(ti));
        when(wpInstRepo.findByOrgKeyAndWorkflowInstanceIdAndArtifactDefinitionId(ORG, instanceId, "wp1")).thenReturn(Optional.of(wpi));
        when(wpInstRepo.findByOrgKeyAndWorkflowInstanceId(ORG, instanceId)).thenReturn(List.of(wpi));

        assertThat(findTask.find(ORG, instanceId, "t1")).isEqualTo(ti);
        assertThat(listTasks.findAll(ORG, instanceId)).containsExactly(ti);
        assertThat(findWp.find(ORG, instanceId, "wp1")).isEqualTo(wpi);
        assertThat(listWps.findAll(ORG, instanceId)).containsExactly(wpi);

        when(taskInstRepo.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(ORG, instanceId, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findTask.find(ORG, instanceId, "unknown")).isInstanceOf(NotFoundException.class);

        when(wpInstRepo.findByOrgKeyAndWorkflowInstanceIdAndArtifactDefinitionId(ORG, instanceId, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findWp.find(ORG, instanceId, "unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void toolStepExecutor_executesAllBranches() {
        ToolStepExecutor executor = new ToolStepExecutor(toolDefRepo, toolInvocationPort);

        // Step without tool
        StepDefinition manualStep = StepDefinition.builder().id("s1").name("Manual Step").build();
        List<StepResult> r1 = executor.execute(ORG, List.of(manualStep), new HashMap<>());
        assertThat(r1).hasSize(1);
        assertThat(r1.get(0).getStepId()).isEqualTo("s1");
        assertThat(r1.get(0).getError()).isNull();

        // Step with missing tool
        StepDefinition missingToolStep = StepDefinition.builder().id("s2").toolDefinitionId("missing-tool").build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "missing-tool")).thenReturn(Optional.empty());
        List<StepResult> r2 = executor.execute(ORG, List.of(missingToolStep), new HashMap<>());
        assertThat(r2.get(0).getError()).contains("not found");

        // Step with missing operation
        ToolDefinition tool = ToolDefinition.builder().id("tool1").operations(List.of()).build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(tool));
        StepDefinition missingOpStep = StepDefinition.builder().id("s3").toolDefinitionId("tool1").toolOperation("missing-op").build();
        List<StepResult> r3 = executor.execute(ORG, List.of(missingOpStep), new HashMap<>());
        assertThat(r3.get(0).getError()).contains("Operation 'missing-op' not found");

        // Successful invocation with input and output mapping
        ToolOperation op = ToolOperation.builder().id("op1").build();
        ToolDefinition toolWithOp = ToolDefinition.builder().id("tool1").operations(List.of(op)).build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(toolWithOp));

        StepDefinition fullStep = StepDefinition.builder()
                .id("s4")
                .toolDefinitionId("tool1")
                .toolOperation("op1")
                .inputMapping(Map.of("inParam", "ctxParam"))
                .outputMapping(Map.of("ctxResult", "respResult"))
                .build();

        Map<String, Object> context = new HashMap<>();
        context.put("ctxParam", "myVal");

        when(toolInvocationPort.invoke(eq(toolWithOp), eq(op), any()))
                .thenReturn(new ToolInvocationResult(true, 200, Map.of("respResult", "outVal"), null));

        List<StepResult> r4 = executor.execute(ORG, List.of(fullStep), context);
        assertThat(r4.get(0).getError()).isNull();
        assertThat(context).containsEntry("ctxResult", "outVal");

        // Failed invocation
        when(toolInvocationPort.invoke(eq(toolWithOp), eq(op), any()))
                .thenReturn(new ToolInvocationResult(false, 500, null, "Internal Server Error"));
        List<StepResult> r5 = executor.execute(ORG, List.of(fullStep), context);
        assertThat(r5.get(0).getError()).isEqualTo("Internal Server Error");
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * The execution layer works against a {@link ResolvedWorkflow}, not a {@code Workflow}: the split
     * of the definition layer into catalog aggregates is an authoring and storage concern, and
     * {@code ResolveWorkflowUseCase} is the seam that hides it. These fixtures build the
     * resolved view directly, which is also why nothing here stubs a definition repository.
     */
    private ResolvedWorkflow resolved(String id, String name, List<ArtifactDefinition> artifacts, TaskDefinition... tasks) {
        return resolvedWithRole(id, name, artifacts, null, null, tasks);
    }

    private ResolvedWorkflow resolvedWithRole(String id, RoleDefinition role, String performedBy, TaskDefinition... tasks) {
        return resolvedWithRole(id, id, List.of(), role, performedBy, tasks);
    }

    private ResolvedWorkflow resolvedWithRole(String id, String name, List<ArtifactDefinition> artifacts,
                                              RoleDefinition role, String performedBy, TaskDefinition... tasks) {
        List<ResolvedWorkflow.ResolvedTask> resolvedTasks = List.of(tasks).stream()
                .map(task -> new ResolvedWorkflow.ResolvedTask(
                        TaskUse.builder().taskDefinitionId(task.getId()).performedBy(performedBy).build(), task))
                .toList();
        Workflow definition = Workflow.builder()
                .orgKey(ORG)
                .id(id)
                .name(name)
                .roles(role == null ? List.of() : List.of(RoleUse.builder().roleDefinitionId(role.getId()).build()))
                .artifacts(artifacts.stream()
                        .map(artifact -> ArtifactUse.builder().artifactDefinitionId(artifact.getId()).build()).toList())
                .tasks(resolvedTasks.stream().map(ResolvedWorkflow.ResolvedTask::assignment).toList())
                .build();
        return new ResolvedWorkflow(definition, role == null ? List.of() : List.of(role), artifacts, resolvedTasks);
    }
}
