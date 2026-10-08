package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.TimerDefinition;
import com.processpuzzle.workflow.definition.domain.TimerType;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow.ResolvedTask;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link TaskActivationService} with timers and boundary events: intermediate timers armed with a due
 * time, boundaries armed while their task runs and cancelled once it ends, and dead-path elimination.
 */
class TaskActivationTimersTest {

    private static final String ORG = "acme";
    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private final List<TaskInstance> tasks = new ArrayList<>();
    private final List<EventInstance> events = new ArrayList<>();
    private TaskActivationService service;

    @BeforeEach
    void setUp() {
        TaskInstanceRepository taskRepository = mock(TaskInstanceRepository.class);
        EventInstanceRepository eventRepository = mock(EventInstanceRepository.class);
        WorkflowInstanceRepository instanceRepository = mock(WorkflowInstanceRepository.class);
        RuleEvaluationPort rules = mock(RuleEvaluationPort.class);
        when(rules.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskRepository.findByOrgKeyAndWorkflowInstanceId(ORG, INSTANCE_ID)).thenReturn(tasks);
        when(eventRepository.findByOrgKeyAndWorkflowInstanceId(ORG, INSTANCE_ID)).thenAnswer(call -> List.copyOf(events));
        when(eventRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(instanceRepository.findByOrgKeyAndId(ORG, INSTANCE_ID)).thenReturn(Optional.of(WorkflowInstance.builder()
                .id(INSTANCE_ID).orgKey(ORG).workflowId("invoice").status(WorkflowInstanceStatus.ACTIVE)
                .entityType("order").entityId("order-42").build()));
        service = new TaskActivationService(taskRepository, eventRepository, instanceRepository, rules,
                mock(ApplicationEventPublisher.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- intermediate timers

    @Test
    void aReachedTimerCatchWaitsWithItsDueTime() {
        ResolvedWorkflow workflow = workflow(List.of(task("after", "wait")),
                List.of(timerCatch("wait", TimerType.DURATION, "PT2H")));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        EventInstance wait = event("wait");
        assertThat(wait.getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(wait.getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(wait.getFireCount()).isZero();
        assertThat(wait.getCorrelationValue()).isNull();
        assertThat(taskRow("after").getStatus()).isEqualTo(TaskInstanceStatus.PENDING);
    }

    @Test
    void aTimerPathIsReadFromTheContextAndOneThatDoesNotResolveLeavesItUnarmed() {
        ResolvedWorkflow workflow = workflow(List.of(),
                List.of(timerCatch("deadline", TimerType.DATE, "$.deadline"), timerCatch("missing", TimerType.DATE, "$.nothing")));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of("deadline", "2026-10-10"));

        assertThat(event("deadline").getDueAt()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        assertThat(event("missing").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(event("missing").getDueAt()).isNull();
    }

    // ---------------------------------------------------------------- boundaries

    @Test
    void aBoundaryArmsOnceItsTaskIsActive() {
        ResolvedWorkflow workflow = workflow(List.of(task("issue"), task("escalate", "overdue")),
                List.of(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H")));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(taskRow("issue").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(event("overdue").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(event("overdue").getDueAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(taskRow("escalate").getStatus()).isEqualTo(TaskInstanceStatus.PENDING);
    }

    @Test
    void aBoundaryStaysPendingWhileItsTaskIsNotActive() {
        ResolvedWorkflow workflow = workflow(List.of(task("first"), task("issue", "first")),
                List.of(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H")));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("overdue").getStatus()).isEqualTo(EventInstanceStatus.PENDING);
    }

    @Test
    void aBoundaryOfACatalogEventWaitsOnTheSubject() {
        EventUse confirmed = EventUse.builder().id("confirmed").eventDefinitionId("OrderConfirmedEvent")
                .direction(EventDirection.CATCH).attachedTo("issue").build();
        ResolvedWorkflow workflow = workflow(List.of(task("issue")), List.of(confirmed));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("confirmed").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(event("confirmed").getCorrelationValue()).isEqualTo("order-42");
        assertThat(event("confirmed").getDueAt()).isNull();
    }

    /** issue completed normally: its boundary is withdrawn, and the escalation behind it is unreachable. */
    @Test
    void aTaskEndingNormallyCancelsItsBoundaryAndTheBoundarysSuccessors() {
        ResolvedWorkflow workflow = invoicing();
        pending(workflow);
        taskRow("issue").setStatus(TaskInstanceStatus.COMPLETED);
        event("overdue").setStatus(EventInstanceStatus.WAITING);
        event("overdue").setDueAt(NOW.plusSeconds(60));

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("overdue").getStatus()).isEqualTo(EventInstanceStatus.CANCELLED);
        assertThat(event("overdue").getDueAt()).isNull();
        assertThat(taskRow("escalate").getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
        assertThat(taskRow("escalate").getCancelReason()).isEqualTo(TaskActivationService.UNREACHABLE);
        assertThat(event("issued").getStatus()).isEqualTo(EventInstanceStatus.THROWN);
    }

    /** issue was interrupted: its own successor is unreachable, the escalation runs, the ANY-throw waits for it. */
    @Test
    void anInterruptedTasksSuccessorsAreUnreachableAndTheBoundarysRun() {
        ResolvedWorkflow workflow = workflow(
                List.of(task("issue"), task("archive", "issue"), task("escalate", "overdue")),
                List.of(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H"),
                        anyThrow("issued", "issue", "escalate")));
        pending(workflow);
        taskRow("issue").setStatus(TaskInstanceStatus.CANCELLED);
        event("overdue").setStatus(EventInstanceStatus.OCCURRED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(taskRow("archive").getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
        assertThat(taskRow("escalate").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(event("issued").getStatus()).isEqualTo(EventInstanceStatus.PENDING);
    }

    @Test
    void aCycleThatAlreadyFiredStopsWithItsTask() {
        ResolvedWorkflow workflow = workflow(List.of(task("approve")),
                List.of(boundary("reminder", "approve", false, TimerType.CYCLE, "R3/PT5M")));
        pending(workflow);
        taskRow("approve").setStatus(TaskInstanceStatus.COMPLETED);
        event("reminder").setStatus(EventInstanceStatus.OCCURRED);
        event("reminder").setDueAt(NOW.plusSeconds(300));

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("reminder").getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(event("reminder").getDueAt()).isNull();
    }

    // ---------------------------------------------------------------- dead paths

    @Test
    void anAllJoinIsDeadWhenAnyDependencyIsCancelled() {
        TaskUse join = TaskUse.builder().taskDefinitionId("join").performedBy("clerk")
                .dependsOn(List.of("a", "b")).joinType(JoinType.ALL).build();
        ResolvedWorkflow workflow = workflow(List.of(task("a"), task("b"), resolved(join), task("after", "join")), List.of());
        pending(workflow);
        taskRow("a").setStatus(TaskInstanceStatus.CANCELLED);
        taskRow("b").setStatus(TaskInstanceStatus.ACTIVE);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(taskRow("join").getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
        assertThat(taskRow("after").getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
    }

    @Test
    void anAnyJoinIsDeadOnlyWhenEveryDependencyIsCancelled() {
        TaskUse join = TaskUse.builder().taskDefinitionId("join").performedBy("clerk")
                .dependsOn(List.of("a", "b")).joinType(JoinType.ANY).build();
        ResolvedWorkflow workflow = workflow(List.of(task("a"), task("b"), resolved(join)), List.of());
        pending(workflow);
        taskRow("a").setStatus(TaskInstanceStatus.CANCELLED);
        taskRow("b").setStatus(TaskInstanceStatus.ACTIVE);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());
        assertThat(taskRow("join").getStatus()).isEqualTo(TaskInstanceStatus.PENDING);

        taskRow("b").setStatus(TaskInstanceStatus.CANCELLED);
        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());
        assertThat(taskRow("join").getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
    }

    @Test
    void anEventBehindACancelledTaskIsCancelledToo() {
        ResolvedWorkflow workflow = workflow(List.of(task("a"), task("b")),
                List.of(timerCatch("wait", TimerType.DURATION, "PT1H", "a")));
        pending(workflow);
        taskRow("a").setStatus(TaskInstanceStatus.CANCELLED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("wait").getStatus()).isEqualTo(EventInstanceStatus.CANCELLED);
    }

    // ---------------------------------------------------------------- fixtures

    private static ResolvedWorkflow invoicing() {
        return workflow(List.of(task("issue"), task("escalate", "overdue")),
                List.of(boundary("overdue", "issue", true, TimerType.DURATION, "PT1H"), anyThrow("issued", "issue", "escalate")));
    }

    private static EventUse anyThrow(String id, String... dependsOn) {
        return EventUse.builder().id(id).eventDefinitionId("InvoiceIssued").direction(EventDirection.THROW)
                .dependsOn(Arrays.asList(dependsOn)).joinType(JoinType.ANY).build();
    }

    private static EventUse timerCatch(String id, TimerType type, String expression, String... dependsOn) {
        return EventUse.builder().id(id).direction(EventDirection.CATCH)
                .timer(TimerDefinition.builder().type(type).expression(expression).build())
                .dependsOn(Arrays.asList(dependsOn)).build();
    }

    private static EventUse boundary(String id, String task, boolean interrupting, TimerType type, String expression) {
        return EventUse.builder().id(id).direction(EventDirection.CATCH).attachedTo(task).interrupting(interrupting)
                .timer(TimerDefinition.builder().type(type).expression(expression).build()).build();
    }

    private static ResolvedTask task(String id, String... dependsOn) {
        return resolved(TaskUse.builder().taskDefinitionId(id).performedBy("clerk").dependsOn(Arrays.asList(dependsOn))
                .parallel(true).build());
    }

    private static ResolvedTask resolved(TaskUse use) {
        return new ResolvedTask(use, TaskDefinition.builder().id(use.getTaskDefinitionId()).name(use.getTaskDefinitionId())
                .performedByRoles(List.of("clerk")).build());
    }

    private static ResolvedWorkflow workflow(List<ResolvedTask> resolvedTasks, List<EventUse> eventUses) {
        Workflow definition = Workflow.builder().orgKey(ORG).id("invoice")
                .tasks(resolvedTasks.stream().map(ResolvedTask::assignment).toList())
                .events(eventUses)
                .build();
        return new ResolvedWorkflow(definition, List.of(), List.of(), resolvedTasks);
    }

    private void pending(ResolvedWorkflow workflow) {
        workflow.tasks().forEach(task -> tasks.add(TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG)
                .workflowInstanceId(INSTANCE_ID).taskDefinitionId(task.id()).name(task.id())
                .status(TaskInstanceStatus.PENDING).build()));
        workflow.events().forEach(use -> {
            EventInstance row = TaskActivationService.pendingEventInstance(ORG, INSTANCE_ID, use);
            row.setId(UUID.randomUUID());
            events.add(row);
        });
    }

    private TaskInstance taskRow(String id) {
        return tasks.stream().filter(t -> t.getTaskDefinitionId().equals(id)).findFirst().orElseThrow();
    }

    private EventInstance event(String id) {
        return events.stream().filter(e -> e.getEventUseId().equals(id)).findFirst().orElseThrow();
    }
}
