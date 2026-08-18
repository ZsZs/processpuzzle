package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import com.processpuzzle.workflow.definition.domain.WorkProductType;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StepResult;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.domain.WorkProductInstanceRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowExecutionUseCasesTest {

    private static final String ORG = "org-1";

    private ProcessDefinitionRepository procDefRepo;
    private ProcessInstanceRepository procInstRepo;
    private TaskInstanceRepository taskInstRepo;
    private WorkProductInstanceRepository wpInstRepo;
    private TaskActivationService taskActivationService;
    private ApplicationEventPublisher eventPublisher;
    private RuleEvaluationPort ruleEvaluationPort;
    private ToolDefinitionRepository toolDefRepo;
    private ToolInvocationPort toolInvocationPort;
    private RoleMembershipPort roleMembershipPort;

    @BeforeEach
    void setUp() {
        procDefRepo = mock(ProcessDefinitionRepository.class);
        procInstRepo = mock(ProcessInstanceRepository.class);
        taskInstRepo = mock(TaskInstanceRepository.class);
        wpInstRepo = mock(WorkProductInstanceRepository.class);
        taskActivationService = mock(TaskActivationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ruleEvaluationPort = mock(RuleEvaluationPort.class);
        toolDefRepo = mock(ToolDefinitionRepository.class);
        toolInvocationPort = mock(ToolInvocationPort.class);
        roleMembershipPort = mock(RoleMembershipPort.class);
    }

    @Test
    void startProcessInstanceUseCase_startsProcessWithTasksAndWorkProducts() {
        StartProcessInstanceUseCase useCase = new StartProcessInstanceUseCase(
                procDefRepo, procInstRepo, taskInstRepo, wpInstRepo, taskActivationService, eventPublisher);

        WorkProductDefinition wpDef = WorkProductDefinition.builder().id("wp-1").name("Doc").type(WorkProductType.ARTIFACT).build();
        TaskDefinition taskDef = TaskDefinition.builder().id("t-1").name("Task 1").build();
        ProcessDefinition procDef = ProcessDefinition.builder()
                .orgKey(ORG)
                .id("proc-1")
                .name("Proc 1")
                .workProducts(List.of(wpDef))
                .tasks(List.of(taskDef))
                .build();

        when(procDefRepo.findByOrgKeyAndId(ORG, "proc-1")).thenReturn(Optional.of(procDef));
        when(procInstRepo.save(any(ProcessInstance.class))).thenAnswer(inv -> {
            ProcessInstance pi = inv.getArgument(0);
            pi.setId(UUID.randomUUID());
            return pi;
        });
        when(wpInstRepo.save(any(WorkProductInstance.class))).thenAnswer(inv -> {
            WorkProductInstance wpi = inv.getArgument(0);
            wpi.setId(UUID.randomUUID());
            return wpi;
        });

        ProcessInstance started = useCase.start(ORG, "proc-1", "entity-1", Map.of("initKey", "initVal"));
        assertThat(started).isNotNull();
        assertThat(started.getStatus()).isEqualTo(ProcessInstanceStatus.ACTIVE);
        assertThat(started.getContext()).containsEntry("initKey", "initVal");

        verify(taskActivationService).activateEligibleTasks(eq(ORG), eq(procDef), eq(started.getId()), any());

        // Start unknown process definition
        when(procDefRepo.findByOrgKeyAndId(ORG, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.start(ORG, "unknown", "entity-1", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelProcessInstanceUseCase_successAndConflict() {
        CancelProcessInstanceUseCase useCase = new CancelProcessInstanceUseCase(procInstRepo, eventPublisher);
        UUID instanceId = UUID.randomUUID();
        ProcessInstance active = ProcessInstance.builder().id(instanceId).orgKey(ORG).status(ProcessInstanceStatus.ACTIVE).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(active));

        useCase.cancel(ORG, instanceId, "User requested");
        assertThat(active.getStatus()).isEqualTo(ProcessInstanceStatus.CANCELLED);
        verify(procInstRepo).save(active);

        // Cancel already cancelled / completed throws ConflictException
        ProcessInstance cancelled = ProcessInstance.builder().id(instanceId).orgKey(ORG).status(ProcessInstanceStatus.CANCELLED).build();
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(cancelled));
        assertThatThrownBy(() -> useCase.cancel(ORG, instanceId, "Again"))
                .isInstanceOf(ConflictException.class);

        // Cancel unknown
        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.cancel(ORG, instanceId, "Again"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAndFindAllProcessInstancesUseCase() {
        FindProcessInstanceUseCase findUseCase = new FindProcessInstanceUseCase(procInstRepo);
        FindAllProcessInstancesUseCase findAllUseCase = new FindAllProcessInstancesUseCase(procInstRepo);

        UUID instanceId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        ProcessInstance pi = ProcessInstance.builder().id(instanceId).orgKey(ORG).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(procInstRepo.findByOrgKeyAndId(ORG, unknownId)).thenReturn(Optional.empty());

        assertThat(findUseCase.findByOrgKeyAndId(ORG, instanceId)).isEqualTo(pi);
        assertThatThrownBy(() -> findUseCase.findByOrgKeyAndId(ORG, unknownId))
                .isInstanceOf(NotFoundException.class);

        when(procInstRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pi)));

        FindAllProcessInstancesUseCase.Query query = new FindAllProcessInstancesUseCase.Query(
                ORG, "proc-1", ProcessInstanceStatus.ACTIVE, "entity-1", "status == 'ACTIVE'", "startedAt,desc", 0, 10);
        Page<ProcessInstance> result = findAllUseCase.findAll(query);
        assertThat(result.getContent()).containsExactly(pi);
    }

    @Test
    void assignTaskUseCase_successAndRoleValidation() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RoleMembershipPort> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(roleMembershipPort);

        AssignTaskUseCase useCase = new AssignTaskUseCase(procInstRepo, procDefRepo, taskInstRepo, provider);

        UUID instanceId = UUID.randomUUID();
        ProcessInstance pi = ProcessInstance.builder().id(instanceId).orgKey(ORG).processDefinitionId("proc-1").build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).processInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();
        RoleDefinition role = RoleDefinition.builder().id("dev").name("Dev").entityRoleId("role-dev").build();
        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1").performedBy("dev").build();
        ProcessDefinition procDef = ProcessDefinition.builder().orgKey(ORG).id("proc-1").roles(List.of(role)).tasks(List.of(taskDef)).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(procDefRepo.findByOrgKeyAndId(ORG, "proc-1")).thenReturn(Optional.of(procDef));
        when(taskInstRepo.save(ti)).thenReturn(ti);

        // Role membership ok
        when(roleMembershipPort.isMember(ORG, "user-1", "role-dev")).thenReturn(true);
        TaskInstance assigned = useCase.assign(ORG, instanceId, "task-1", "user-1");
        assertThat(assigned.getAssignedTo()).isEqualTo("user-1");

        // PerformedBy is null -> assigns without role check
        TaskDefinition unassignedTask = TaskDefinition.builder().id("task-1").name("Task 1").performedBy(null).build();
        procDef.setTasks(List.of(unassignedTask));
        TaskInstance unassignedResult = useCase.assign(ORG, instanceId, "task-1", "user-any");
        assertThat(unassignedResult.getAssignedTo()).isEqualTo("user-any");
        procDef.setTasks(List.of(taskDef));

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
                procInstRepo, procDefRepo, taskInstRepo, ruleEvaluationPort, stepExecutor, taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        ProcessInstance pi = ProcessInstance.builder().id(instanceId).orgKey(ORG).processDefinitionId("proc-1")
                .status(ProcessInstanceStatus.ACTIVE).context(new HashMap<>(Map.of("a", "1"))).build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).processInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();
        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1").postconditionRuleId("rule-post").steps(List.of()).build();
        ProcessDefinition procDef = ProcessDefinition.builder().orgKey(ORG).id("proc-1").tasks(List.of(taskDef)).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(procDefRepo.findByOrgKeyAndId(ORG, "proc-1")).thenReturn(Optional.of(procDef));

        // Postcondition failed
        when(ruleEvaluationPort.evaluate(eq(ORG), eq("rule-post"), any()))
                .thenReturn(new RuleCheckResult(false, "Postcondition violation"));

        CompleteTaskUseCase.Result failedResult = useCase.complete(ORG, instanceId, "task-1", Map.of("extra", "2"));
        assertThat(failedResult.accepted()).isFalse();
        assertThat(failedResult.postconditionDetail()).isEqualTo("Postcondition violation");
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);

        // Postcondition passed, all terminal -> completes process instance
        when(ruleEvaluationPort.evaluate(eq(ORG), eq("rule-post"), any()))
                .thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(true);

        CompleteTaskUseCase.Result successResult = useCase.complete(ORG, instanceId, "task-1", Map.of("extra", "2"));
        assertThat(successResult.accepted()).isTrue();
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.COMPLETED);
        assertThat(pi.getStatus()).isEqualTo(ProcessInstanceStatus.COMPLETED);

        // Steps execution during completion
        StepDefinition step = StepDefinition.builder().id("s1").build();
        TaskDefinition taskWithSteps = TaskDefinition.builder().id("task-1").name("Task 1").postconditionRuleId("rule-post").steps(List.of(step)).build();
        procDef.setTasks(List.of(taskWithSteps));
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

    @Test
    void skipTaskUseCase_successAndConflict() {
        SkipTaskUseCase useCase = new SkipTaskUseCase(
                procInstRepo, procDefRepo, taskInstRepo, taskActivationService, eventPublisher);

        UUID instanceId = UUID.randomUUID();
        ProcessInstance pi = ProcessInstance.builder().id(instanceId).orgKey(ORG).processDefinitionId("proc-1")
                .status(ProcessInstanceStatus.ACTIVE).context(new HashMap<>()).build();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).processInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).build();

        TaskDefinition taskDef = TaskDefinition.builder().id("task-1").name("Task 1").build();
        ProcessDefinition proc = ProcessDefinition.builder().orgKey(ORG).id("proc-1").tasks(List.of(taskDef)).build();

        when(procInstRepo.findByOrgKeyAndId(ORG, instanceId)).thenReturn(Optional.of(pi));
        when(taskInstRepo.findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(ORG, instanceId, "task-1")).thenReturn(Optional.of(ti));
        when(taskInstRepo.save(ti)).thenReturn(ti);
        when(procDefRepo.findByOrgKeyAndId(ORG, "proc-1")).thenReturn(Optional.of(proc));
        when(taskActivationService.allTerminal(ORG, instanceId)).thenReturn(true);

        TaskInstance skipped = useCase.skip(ORG, instanceId, "task-1", "Not needed");
        assertThat(skipped.getStatus()).isEqualTo(TaskInstanceStatus.SKIPPED);
        assertThat(pi.getStatus()).isEqualTo(ProcessInstanceStatus.COMPLETED);

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
    void findAndListTaskAndWorkProductUseCases() {
        FindTaskInstanceUseCase findTask = new FindTaskInstanceUseCase(taskInstRepo);
        ListTaskInstancesUseCase listTasks = new ListTaskInstancesUseCase(taskInstRepo);
        FindWorkProductInstanceUseCase findWp = new FindWorkProductInstanceUseCase(wpInstRepo);
        ListWorkProductInstancesUseCase listWps = new ListWorkProductInstancesUseCase(wpInstRepo);

        UUID instanceId = UUID.randomUUID();
        TaskInstance ti = TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).processInstanceId(instanceId).taskDefinitionId("t1").build();
        WorkProductInstance wpi = WorkProductInstance.builder().id(UUID.randomUUID()).orgKey(ORG).processInstanceId(instanceId).workProductDefinitionId("wp1").build();

        when(taskInstRepo.findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(ORG, instanceId, "t1")).thenReturn(Optional.of(ti));
        when(taskInstRepo.findByOrgKeyAndProcessInstanceId(ORG, instanceId)).thenReturn(List.of(ti));
        when(wpInstRepo.findByOrgKeyAndProcessInstanceIdAndWorkProductDefinitionId(ORG, instanceId, "wp1")).thenReturn(Optional.of(wpi));
        when(wpInstRepo.findByOrgKeyAndProcessInstanceId(ORG, instanceId)).thenReturn(List.of(wpi));

        assertThat(findTask.find(ORG, instanceId, "t1")).isEqualTo(ti);
        assertThat(listTasks.findAll(ORG, instanceId)).containsExactly(ti);
        assertThat(findWp.find(ORG, instanceId, "wp1")).isEqualTo(wpi);
        assertThat(listWps.findAll(ORG, instanceId)).containsExactly(wpi);

        when(taskInstRepo.findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(ORG, instanceId, "unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> findTask.find(ORG, instanceId, "unknown")).isInstanceOf(NotFoundException.class);

        when(wpInstRepo.findByOrgKeyAndProcessInstanceIdAndWorkProductDefinitionId(ORG, instanceId, "unknown")).thenReturn(Optional.empty());
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
        StepDefinition missingToolStep = StepDefinition.builder().id("s2").toolId("missing-tool").build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "missing-tool")).thenReturn(Optional.empty());
        List<StepResult> r2 = executor.execute(ORG, List.of(missingToolStep), new HashMap<>());
        assertThat(r2.get(0).getError()).contains("not found");

        // Step with missing operation
        ToolDefinition tool = ToolDefinition.builder().id("tool1").operations(List.of()).build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(tool));
        StepDefinition missingOpStep = StepDefinition.builder().id("s3").toolId("tool1").toolOperation("missing-op").build();
        List<StepResult> r3 = executor.execute(ORG, List.of(missingOpStep), new HashMap<>());
        assertThat(r3.get(0).getError()).contains("Operation 'missing-op' not found");

        // Successful invocation with input and output mapping
        ToolOperation op = ToolOperation.builder().id("op1").build();
        ToolDefinition toolWithOp = ToolDefinition.builder().id("tool1").operations(List.of(op)).build();
        when(toolDefRepo.findByOrgKeyAndId(ORG, "tool1")).thenReturn(Optional.of(toolWithOp));

        StepDefinition fullStep = StepDefinition.builder()
                .id("s4")
                .toolId("tool1")
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
}
