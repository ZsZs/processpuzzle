package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportWorkflowsUseCase;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;
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

/**
 * The invoice round trip of the testbed seed, through the real event path: a workflow throws a
 * MESSAGE, base-event republishes it, it starts a second workflow, and that one's answer is caught by
 * the first — with the Spring Modulith event publication registry carrying every hop.
 *
 * <p>Runs on a trimmed copy of the seed in an organization of its own: the seed's tasks call an
 * external tool and evaluate base-rule rules, neither of which this test is about. The events, their
 * correlation and the flow around them are the seed's.
 */
@SpringBootTest
class InvoiceRoundTripIntegrationTest {

    private static final String ORG = "invoice-round-trip";
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
                    payloadMapping: { orderNumber: $.orderNumber }
                  - id: invoice-issued
                    eventDefinitionId: InvoiceIssued
                    direction: CATCH
                    dependsOn: [ request-invoice ]
                    correlationKey: orderId
                    payloadMapping: { invoiceNumber: $.payload.invoiceNumber }
              - id: invoicing-workflow
                name: Invoicing
                startEvents:
                  - id: invoice-requested
                    startType: TRIGGERING_EVENT
                    eventType: InvoiceRequested
                    payloadMapping: { orderId: $.correlationValue, orderNumber: $.payload.orderNumber }
                roles: [ { roleDefinitionId: manager } ]
                tasks:
                  - { taskDefinitionId: issue-invoice, performedBy: manager }
                events:
                  - id: invoice-issued
                    eventDefinitionId: InvoiceIssued
                    direction: THROW
                    dependsOn: [ issue-invoice ]
                    correlationKey: orderId
                    payloadMapping: { invoiceNumber: $.invoiceNumber }
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

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEventDefinitions.execute(ORG, stream(EVENTS)).errors()).isEmpty();
        assertThat(importWorkflows.execute(ORG, stream(WORKFLOWS)).errors()).isEmpty();
    }

    @Test
    void anOrderAsksForItsInvoiceAndWaitsForTheAnswer() {
        String orderId = "order-" + UUID.randomUUID();
        WorkflowInstance order = startWorkflowInstance.start(ORG, "order-fulfillment-workflow", orderId, null,
                Map.of("orderId", orderId, "orderNumber", "O-1"));

        completeTask.complete(ORG, order.getId(), "approve-shipment", Map.of());

        WorkflowInstance invoicing = await("an invoicing instance for the order", () -> invoicingFor(orderId));
        assertThat(eventOf(order.getId(), "request-invoice").getStatus()).isEqualTo(EventInstanceStatus.THROWN);
        assertThat(eventOf(order.getId(), "invoice-issued").getStatus()).isEqualTo(EventInstanceStatus.WAITING);
        assertThat(contextOf(invoicing.getId())).containsEntry("orderId", orderId).containsEntry("orderNumber", "O-1");

        completeTask.complete(ORG, invoicing.getId(), "issue-invoice", Map.of("invoiceNumber", "INV-1"));

        await("the order's invoice-issued catch to occur", () -> Optional.of(eventOf(order.getId(), "invoice-issued"))
                .filter(event -> event.getStatus() == EventInstanceStatus.OCCURRED));
        assertThat(taskOf(order.getId(), "confirm-delivery").getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(contextOf(order.getId())).containsEntry("invoiceNumber", "INV-1");
        assertThat(findWorkflowInstance.findByOrgKeyAndId(ORG, invoicing.getId()).getStatus())
                .isEqualTo(WorkflowInstanceStatus.COMPLETED);

        completeTask.complete(ORG, order.getId(), "confirm-delivery", Map.of());

        assertThat(findWorkflowInstance.findByOrgKeyAndId(ORG, order.getId()).getStatus())
                .isEqualTo(WorkflowInstanceStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- helpers

    private Optional<WorkflowInstance> invoicingFor(String orderId) {
        return findAllWorkflowInstances.findAll(new FindAllWorkflowInstancesUseCase.Query(
                ORG, "invoicing-workflow", null, orderId, null, null, null, null)).stream().findFirst();
    }

    private EventInstance eventOf(UUID instanceId, String eventUseId) {
        return listEventInstances.findAll(ORG, instanceId).stream()
                .filter(event -> event.getEventUseId().equals(eventUseId)).findFirst().orElseThrow();
    }

    private TaskInstance taskOf(UUID instanceId, String taskId) {
        return listTaskInstances.findAll(ORG, instanceId).stream()
                .filter(task -> task.getTaskDefinitionId().equals(taskId)).findFirst().orElseThrow();
    }

    private Map<String, Object> contextOf(UUID instanceId) {
        List<TaskInstance> tasks = listTaskInstances.findAll(ORG, instanceId);
        return WorkflowContext.assemble(findWorkflowInstance.findByOrgKeyAndId(ORG, instanceId), tasks,
                listEventInstances.findAll(ORG, instanceId));
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
