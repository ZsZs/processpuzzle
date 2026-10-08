package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.EventThrown;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskUse;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link TaskActivationService} with intermediate events in the flow: throws raised to a fixed
 * point, catches started waiting with the right correlation value, obsolete catches withdrawn, and
 * rows reconciled with the live definition.
 */
class TaskActivationEventsTest {

    private static final String ORG = "acme";
    private static final UUID INSTANCE_ID = UUID.randomUUID();

    private final List<TaskInstance> tasks = new ArrayList<>();
    private final List<EventInstance> events = new ArrayList<>();
    private TaskInstanceRepository taskRepository;
    private EventInstanceRepository eventRepository;
    private ApplicationEventPublisher publisher;
    private TaskActivationService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskInstanceRepository.class);
        eventRepository = mock(EventInstanceRepository.class);
        WorkflowInstanceRepository instanceRepository = mock(WorkflowInstanceRepository.class);
        RuleEvaluationPort rules = mock(RuleEvaluationPort.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(rules.evaluate(any(), any(), any())).thenReturn(RuleCheckResult.ALWAYS_PASSES);
        when(taskRepository.findByOrgKeyAndWorkflowInstanceId(ORG, INSTANCE_ID)).thenReturn(tasks);
        when(eventRepository.findByOrgKeyAndWorkflowInstanceId(ORG, INSTANCE_ID)).thenAnswer(invocation -> List.copyOf(events));
        when(eventRepository.save(any())).thenAnswer(invocation -> {
            EventInstance saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            if (!events.contains(saved)) {
                events.add(saved);
            }
            return saved;
        });
        when(instanceRepository.findByOrgKeyAndId(ORG, INSTANCE_ID)).thenReturn(Optional.of(WorkflowInstance.builder()
                .id(INSTANCE_ID).orgKey(ORG).workflowId("order").status(WorkflowInstanceStatus.ACTIVE)
                .entityType("order").entityId("order-42").build()));
        service = new TaskActivationService(taskRepository, eventRepository, instanceRepository, rules, publisher);
    }

    @Test
    void aThrowWithoutDependenciesIsRaisedAtStart() {
        ResolvedWorkflow workflow = workflow(List.of(task("review")),
                List.of(throwing("ask", "InvoiceRequested", "orderId", Map.of("number", "$.orderNumber"))));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of("orderId", "order-42", "orderNumber", "O-1"));

        EventInstance ask = event("ask");
        assertThat(ask.getStatus()).isEqualTo(EventInstanceStatus.THROWN);
        assertThat(ask.getOccurrenceId()).isEqualTo(ask.getId());
        EventThrown thrown = thrown().getFirst();
        assertThat(thrown.eventDefinitionId()).isEqualTo("InvoiceRequested");
        assertThat(thrown.occurrenceId()).isEqualTo(ask.getId());
        assertThat(thrown.correlationValue()).isEqualTo("order-42");
        assertThat(thrown.payload()).containsEntry("number", "O-1");
        assertThat(thrown.subjectType()).isEqualTo("order");
        assertThat(thrown.subjectId()).isEqualTo("order-42");
        assertThat(thrown.sourceWorkflowInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(thrown.sourceWorkflowId()).isEqualTo("order");
    }

    @Test
    void taskThenThrowThenTaskAdvanceInOneCall() {
        ResolvedWorkflow workflow = workflow(
                List.of(task("first"), task("second", "notify")),
                List.of(throwing("notify", "Notified", null, null, "first")));
        pending(workflow);
        taskRow("first").setStatus(TaskInstanceStatus.COMPLETED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("notify").getStatus()).isEqualTo(EventInstanceStatus.THROWN);
        assertThat(taskRow("second").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(thrown().getFirst().payload()).isEmpty();
    }

    @Test
    void aCatchAfterAThrowStartsWaitingOnItsCorrelationValue() {
        ResolvedWorkflow workflow = workflow(
                List.of(task("confirm", "issued")),
                List.of(throwing("ask", "InvoiceRequested", "orderId", null),
                        catching("issued", "InvoiceIssued", "orderId", "ask")));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of("orderId", 42));

        EventInstance issued = event("issued");
        assertThat(issued.getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(issued.getCorrelationValue()).isEqualTo("42");
        assertThat(issued.getWaitingSince()).isNotNull();
        assertThat(taskRow("confirm").getStatus()).isEqualTo(TaskInstanceStatus.PENDING);
    }

    @Test
    void aCatchWithoutCorrelationKeyWaitsForTheInstancesSubject() {
        ResolvedWorkflow workflow = workflow(List.of(task("ship", "confirmed")),
                List.of(catching("confirmed", "OrderConfirmedEvent", null)));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("confirmed").getCorrelationValue()).isEqualTo("order-42");
    }

    @Test
    void anOccurredCatchSatisfiesItsDependents() {
        ResolvedWorkflow workflow = workflow(List.of(task("ship", "confirmed")),
                List.of(catching("confirmed", "OrderConfirmedEvent", null)));
        pending(workflow);
        event("confirmed").setStatus(EventInstanceStatus.OCCURRED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(taskRow("ship").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    @Test
    void anAnyJoinThatWentAheadWithdrawsTheCatchItNoLongerNeeds() {
        TaskUse join = TaskUse.builder().taskDefinitionId("join").performedBy("clerk")
                .dependsOn(List.of("manual", "confirmed")).joinType(JoinType.ANY).build();
        ResolvedWorkflow workflow = workflow(List.of(task("manual"), resolved(join)),
                List.of(catching("confirmed", "OrderConfirmedEvent", null)));
        pending(workflow);
        taskRow("manual").setStatus(TaskInstanceStatus.COMPLETED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(taskRow("join").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(event("confirmed").getStatus()).isEqualTo(EventInstanceStatus.CANCELLED);
    }

    @Test
    void aCatchNothingDependsOnKeepsWaiting() {
        ResolvedWorkflow workflow = workflow(List.of(task("only")),
                List.of(catching("loose", "OrderConfirmedEvent", null)));
        pending(workflow);
        taskRow("only").setStatus(TaskInstanceStatus.COMPLETED);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("loose").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
    }

    @Test
    void anEventUseAddedSinceTheStartGetsItsRow() {
        ResolvedWorkflow workflow = workflow(List.of(task("only")),
                List.of(catching("added", "OrderConfirmedEvent", null)));
        tasks.add(taskInstance("only"));

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(event("added").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(event("added").getEventDefinitionId()).isEqualTo("OrderConfirmedEvent");
    }

    @Test
    void eventsTakeNoPartInTheSiblingRule() {
        ResolvedWorkflow workflow = workflow(List.of(task("a"), task("b")),
                List.of(catching("c", "OrderConfirmedEvent", null)));
        pending(workflow);

        service.activateEligibleTasks(ORG, workflow, INSTANCE_ID, Map.of());

        assertThat(tasks.stream().filter(t -> t.getStatus() == TaskInstanceStatus.ACTIVE)).hasSize(1);
        assertThat(event("c").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
    }

    @Test
    void allTerminalCountsOnlyTheLiveDefinitionsOpenEvents() {
        ResolvedWorkflow workflow = workflow(List.of(task("only")),
                List.of(catching("loose", "OrderConfirmedEvent", null)));
        var open = EnumSet.of(EventInstanceStatus.PENDING, EventInstanceStatus.WAITING);
        when(eventRepository.countByOrgKeyAndWorkflowInstanceIdAndEventUseIdInAndStatusIn(
                ORG, INSTANCE_ID, List.of("loose"), open)).thenReturn(1L, 0L);

        assertThat(service.allTerminal(ORG, workflow, INSTANCE_ID)).isFalse();
        assertThat(service.allTerminal(ORG, workflow, INSTANCE_ID)).isTrue();
        verify(eventRepository, atLeastOnce()).countByOrgKeyAndWorkflowInstanceIdAndEventUseIdInAndStatusIn(
                eq(ORG), eq(INSTANCE_ID), eq(List.of("loose")), eq(open));
    }

    // ---------------------------------------------------------------- fixtures

    private List<EventThrown> thrown() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(EventThrown.class::isInstance).map(EventThrown.class::cast).toList();
    }

    private static ResolvedTask task(String id, String... dependsOn) {
        return resolved(TaskUse.builder().taskDefinitionId(id).performedBy("clerk").dependsOn(Arrays.asList(dependsOn)).build());
    }

    private static ResolvedTask resolved(TaskUse use) {
        return new ResolvedTask(use, TaskDefinition.builder().id(use.getTaskDefinitionId()).name(use.getTaskDefinitionId())
                .performedByRoles(List.of("clerk")).build());
    }

    private static EventUse throwing(String id, String definition, String correlationKey, Map<String, String> payload,
                                     String... dependsOn) {
        return EventUse.builder().id(id).eventDefinitionId(definition).direction(EventDirection.THROW)
                .correlationKey(correlationKey).payloadMapping(payload).dependsOn(Arrays.asList(dependsOn)).build();
    }

    private static EventUse catching(String id, String definition, String correlationKey, String... dependsOn) {
        return EventUse.builder().id(id).eventDefinitionId(definition).direction(EventDirection.CATCH)
                .correlationKey(correlationKey).dependsOn(Arrays.asList(dependsOn)).build();
    }

    private static ResolvedWorkflow workflow(List<ResolvedTask> resolvedTasks, List<EventUse> eventUses) {
        Workflow definition = Workflow.builder().orgKey(ORG).id("order")
                .tasks(resolvedTasks.stream().map(ResolvedTask::assignment).toList())
                .events(eventUses)
                .build();
        return new ResolvedWorkflow(definition, List.of(), List.of(), resolvedTasks);
    }

    private void pending(ResolvedWorkflow workflow) {
        workflow.tasks().forEach(task -> tasks.add(taskInstance(task.id())));
        workflow.events().forEach(use -> {
            EventInstance row = TaskActivationService.pendingEventInstance(ORG, INSTANCE_ID, use);
            row.setId(UUID.randomUUID());
            events.add(row);
        });
    }

    private static TaskInstance taskInstance(String id) {
        return TaskInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(INSTANCE_ID)
                .taskDefinitionId(id).name(id).status(TaskInstanceStatus.PENDING).build();
    }

    private TaskInstance taskRow(String id) {
        return tasks.stream().filter(t -> t.getTaskDefinitionId().equals(id)).findFirst().orElseThrow();
    }

    private EventInstance event(String id) {
        return events.stream().filter(e -> e.getEventUseId().equals(id)).findFirst().orElseThrow();
    }
}
