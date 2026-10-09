package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportWorkflowsUseCase;
import com.processpuzzle.workflow.execution.adapters.inbound.TimerSweep;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllWorkflowInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindWorkflowInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListEventInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Timers, boundary events and self-starting start events of the testbed seed, end to end. Time is not
 * waited for: a due time is moved into the past and {@link TimerSweep#sweep()} is called, which is what
 * the scheduled sweep does every 30 seconds (switched off in the unit-test profile).
 *
 * <p>Runs on a trimmed copy of the seed in an organization of its own, as
 * {@code InvoiceRoundTripIntegrationTest} does, plus a scheduled workflow the seed leaves out on purpose:
 * on stage it would start instances forever.
 */
@SpringBootTest
class WorkflowTimersIntegrationTest {

    private static final String ORG = "workflow-timers";
    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private static final String EVENTS = """
            event-definitions:
              - id: InvoiceRequested
                name: Invoice requested
                kind: MESSAGE
              - id: InvoiceIssued
                name: Invoice issued
                kind: MESSAGE
            """;

    private static final String WORKFLOWS = """
            role-definitions:
              - id: clerk
                name: Clerk
              - id: manager
                name: Manager
            artifact-definitions:
              - id: order-entity
                name: Order
                artifactType: ENTITY
                artifactTypeId: order
            task-definitions:
              - id: approve-shipment
                name: Approve Shipment
                performedByRoles: [ manager ]
              - id: confirm-delivery
                name: Confirm Delivery
                performedByRoles: [ clerk ]
              - id: issue-invoice
                name: Issue Invoice
                performedByRoles: [ manager ]
              - id: escalate-invoice
                name: Escalate Invoice
                performedByRoles: [ manager ]
              - id: collect-feedback
                name: Collect Feedback
                performedByRoles: [ clerk ]
                inputs: [ order-entity ]
            workflows:
              - id: order-fulfillment-workflow
                name: Order Fulfillment
                roles: [ { roleDefinitionId: clerk }, { roleDefinitionId: manager } ]
                tasks:
                  - { taskDefinitionId: approve-shipment, performedBy: manager }
                  - { taskDefinitionId: confirm-delivery, performedBy: clerk, dependsOn: [ invoice-issued ] }
                events:
                  - id: request-invoice
                    eventDefinitionId: InvoiceRequested
                    direction: THROW
                    dependsOn: [ approve-shipment ]
                    correlationKey: orderId
                  - id: invoice-issued
                    eventDefinitionId: InvoiceIssued
                    direction: CATCH
                    dependsOn: [ request-invoice ]
                    correlationKey: orderId
                    payloadMapping: { invoiceNumber: $.payload.invoiceNumber }
                  - id: approval-reminder
                    direction: CATCH
                    attachedTo: approve-shipment
                    interrupting: false
                    timer: { type: CYCLE, expression: R2/PT5M }
              - id: invoicing-workflow
                name: Invoicing
                startEvents:
                  - id: invoice-requested
                    startType: TRIGGERING_EVENT
                    eventType: InvoiceRequested
                    payloadMapping: { orderId: $.correlationValue }
                roles: [ { roleDefinitionId: manager } ]
                tasks:
                  - { taskDefinitionId: issue-invoice, performedBy: manager }
                  - { taskDefinitionId: escalate-invoice, performedBy: manager, dependsOn: [ invoice-overdue ] }
                events:
                  - id: invoice-overdue
                    direction: CATCH
                    attachedTo: issue-invoice
                    timer: { type: DURATION, expression: PT1H }
                  - id: invoice-issued
                    eventDefinitionId: InvoiceIssued
                    direction: THROW
                    dependsOn: [ issue-invoice, escalate-invoice ]
                    joinType: ANY
                    correlationKey: orderId
                    payloadMapping: { invoiceNumber: $.invoiceNumber }
              - id: customer-feedback-workflow
                name: Customer Feedback
                startEvents:
                  - id: order-delivered
                    startType: INPUT_ARTIFACT
                    requiredArtifacts: [ { artifactDefinitionId: order-entity, state: DELIVERED } ]
                    payloadMapping: { orderId: $.subjectId, orderNumber: $.payload.orderNumber }
                roles: [ { roleDefinitionId: clerk } ]
                artifacts: [ { artifactDefinitionId: order-entity } ]
                tasks:
                  - { taskDefinitionId: collect-feedback, performedBy: clerk }
              - id: nightly-check
                name: Nightly Check
                startEvents:
                  - id: every-night
                    startType: TIME_BASED_PRECONDITION
                    timer: { type: CYCLE, expression: R/P1D }
                roles: [ { roleDefinitionId: clerk } ]
                tasks:
                  - { taskDefinitionId: confirm-delivery, performedBy: clerk }
            """;

    @Autowired
    private ImportEventDefinitions importEventDefinitions;
    @Autowired
    private ImportWorkflowsUseCase importWorkflows;
    @Autowired
    private StartWorkflowInstanceUseCase startWorkflowInstance;
    @Autowired
    private CompleteTaskUseCase completeTask;
    @Autowired
    private FindWorkflowInstanceUseCase findWorkflowInstance;
    @Autowired
    private FindAllWorkflowInstancesUseCase findAllWorkflowInstances;
    @Autowired
    private ListTaskInstancesUseCase listTaskInstances;
    @Autowired
    private ListEventInstancesUseCase listEventInstances;
    @Autowired
    private EventInstanceRepository eventInstanceRepository;
    @Autowired
    private StartTimerScheduleRepository startTimerScheduleRepository;
    @Autowired
    private TimerSweep timerSweep;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEventDefinitions.execute(ORG, stream(EVENTS)).errors()).isEmpty();
        assertThat(importWorkflows.execute(ORG, stream(WORKFLOWS)).errors()).isEmpty();
    }

    /** issue-invoice is not done within the hour: the boundary interrupts it and the escalation answers. */
    @Test
    void anOverdueInvoiceIsEscalatedAndStillClosesTheRoundTrip() {
        String orderId = "order-" + UUID.randomUUID();
        WorkflowInstance order = startOrder(orderId);
        completeTask.complete(ORG, order.getId(), "approve-shipment", Map.of());
        WorkflowInstance invoicing = await("an invoicing instance for the order", () -> instanceOf("invoicing-workflow", orderId));
        EventInstance overdue = eventOf(invoicing.getId(), "invoice-overdue");
        assertThat(overdue.getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(overdue.getDueAt()).isAfter(Instant.now().plus(Duration.ofMinutes(59)));

        makeDue(overdue);
        timerSweep.sweep();

        assertThat(eventOf(invoicing.getId(), "invoice-overdue").getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        TaskInstance issue = taskOf(invoicing.getId(), "issue-invoice");
        assertThat(issue.getStatus()).isEqualTo(TaskInstanceStatus.CANCELLED);
        assertThat(issue.getCancelReason()).isEqualTo("interrupted by invoice-overdue");
        assertThat(taskOf(invoicing.getId(), "escalate-invoice").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);

        completeTask.complete(ORG, invoicing.getId(), "escalate-invoice", Map.of("invoiceNumber", "INV-LATE"));

        await("the order's invoice-issued catch to occur", () -> Optional.of(eventOf(order.getId(), "invoice-issued"))
                .filter(event -> event.getStatus() == EventInstanceStatus.OCCURRED));
        assertThat(findWorkflowInstance.findByOrgKeyAndId(ORG, invoicing.getId()).getStatus())
                .isEqualTo(WorkflowInstanceStatus.COMPLETED);
        assertThat(taskOf(order.getId(), "confirm-delivery").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    /** The approval reminder fires twice while approve-shipment waits, then stops; the task keeps running. */
    @Test
    void aCycleBoundaryFiresTwiceThenStops() {
        WorkflowInstance order = startOrder("order-" + UUID.randomUUID());
        assertThat(eventOf(order.getId(), "approval-reminder").getStatus()).isEqualTo(EventInstanceStatus.WAITING);

        makeDue(eventOf(order.getId(), "approval-reminder"));
        timerSweep.sweep();
        EventInstance once = eventOf(order.getId(), "approval-reminder");
        assertThat(once.getStatus()).isEqualTo(EventInstanceStatus.OCCURRED);
        assertThat(once.getFireCount()).isEqualTo(1);
        assertThat(once.getDueAt()).isAfter(Instant.now());

        makeDue(once);
        timerSweep.sweep();
        EventInstance twice = eventOf(order.getId(), "approval-reminder");
        assertThat(twice.getFireCount()).isEqualTo(2);
        assertThat(twice.getDueAt()).isNull();

        timerSweep.sweep();
        assertThat(eventOf(order.getId(), "approval-reminder").getFireCount()).isEqualTo(2);
        assertThat(taskOf(order.getId(), "approve-shipment").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    @Test
    void aTimeBasedStartEventStartsItsWorkflowWhenDue() {
        StartTimerSchedule schedule = await("the nightly start to be scheduled", () -> startTimerScheduleRepository
                .findByOrgKeyAndWorkflowId(ORG, "nightly-check").stream().findFirst());
        assertThat(schedule.getDueAt()).isAfter(Instant.now());
        int before = instancesOf("nightly-check").size();

        schedule.setDueAt(Instant.now().minusSeconds(1));
        startTimerScheduleRepository.save(schedule);
        timerSweep.sweep();

        List<WorkflowInstance> started = instancesOf("nightly-check");
        assertThat(started).hasSize(before + 1);
        assertThat(started).anySatisfy(instance -> {
            assertThat(instance.getStartEventId()).isEqualTo("every-night");
            assertThat(instance.getEntityId()).isNull();
        });
        assertThat(startTimerScheduleRepository.findByOrgKeyAndWorkflowId(ORG, "nightly-check")).singleElement()
                .satisfies(next -> assertThat(next.getDueAt()).isAfter(Instant.now().plus(Duration.ofHours(23))));
    }

    @Test
    void anOrderEnteringDeliveredStartsTheFeedbackWorkflow() {
        String orderId = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(PlatformEvent.stateChanged(
                ORG, "order", orderId, "DELIVERED", Map.of("orderNumber", "O-7"), Instant.now())));

        WorkflowInstance feedback = await("a feedback instance for the order",
                () -> instanceOf("customer-feedback-workflow", orderId));
        assertThat(feedback.getStartEventId()).isEqualTo("order-delivered");
        assertThat(feedback.getEntityType()).isEqualTo("order");
        assertThat(feedback.getInitialContext()).containsEntry("orderId", orderId).containsEntry("orderNumber", "O-7");
        assertThat(taskOf(feedback.getId(), "collect-feedback").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
    }

    // ---------------------------------------------------------------- helpers

    private WorkflowInstance startOrder(String orderId) {
        return startWorkflowInstance.start(ORG, "order-fulfillment-workflow", orderId, null, Map.of("orderId", orderId));
    }

    private void makeDue(EventInstance timer) {
        EventInstance fresh = eventInstanceRepository.findById(timer.getId()).orElseThrow();
        fresh.setDueAt(Instant.now().minusSeconds(1));
        eventInstanceRepository.save(fresh);
    }

    private Optional<WorkflowInstance> instanceOf(String workflowId, String entityId) {
        return findAllWorkflowInstances.findAll(new FindAllWorkflowInstancesUseCase.Query(
                ORG, workflowId, null, entityId, null, null, null, null)).stream().findFirst();
    }

    private List<WorkflowInstance> instancesOf(String workflowId) {
        return findAllWorkflowInstances.findAll(new FindAllWorkflowInstancesUseCase.Query(
                ORG, workflowId, null, null, null, null, null, null)).stream().toList();
    }

    private EventInstance eventOf(UUID instanceId, String eventUseId) {
        return listEventInstances.findAll(ORG, instanceId).stream()
                .filter(event -> event.getEventUseId().equals(eventUseId)).findFirst().orElseThrow();
    }

    private TaskInstance taskOf(UUID instanceId, String taskId) {
        return listTaskInstances.findAll(ORG, instanceId).stream()
                .filter(task -> task.getTaskDefinitionId().equals(taskId)).findFirst().orElseThrow();
    }

    /** The hops are after-commit listeners; poll rather than assume they ran on this thread. */
    private static <T> T await(String what, Supplier<Optional<T>> probe) {
        Instant deadline = Instant.now().plus(PATIENCE);
        while (Instant.now().isBefore(deadline)) {
            Optional<T> value = probe.get();
            if (value.isPresent()) {
                return value.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for " + what, e);
            }
        }
        throw new AssertionError("Timed out waiting for " + what);
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
