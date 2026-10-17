package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.workflow.execution.domain.OccurredEventDocument;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TriggeredStartListenerTest {

    private static final String ORG = "org-1";
    private static final DefinedEventOccurred ORDER_CREATED = new DefinedEventOccurred(
            ORG, "OrderCreatedEvent", "order", "order-1", Map.of("customerName", "ACME"),
            Instant.parse("2026-10-08T10:00:00Z"));

    private WorkflowRepository workflowRepository;
    private StartWorkflowInstanceUseCase startUseCase;
    private TriggeredStartListener listener;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        startUseCase = mock(StartWorkflowInstanceUseCase.class);
        listener = new TriggeredStartListener(workflowRepository, startUseCase);
        when(startUseCase.startTriggered(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(Optional.of(WorkflowInstance.builder().id(UUID.randomUUID()).build()));
    }

    private static Workflow workflow(String id, StartEvent... startEvents) {
        return Workflow.builder().orgKey(ORG).id(id).name(id).startEvents(List.of(startEvents)).build();
    }

    private static StartEvent triggering(String id, String eventType, Map<String, String> mapping) {
        return StartEvent.builder().id(id).startType(WorkflowStartConditionType.TRIGGERING_EVENT)
                .eventType(eventType).payloadMapping(mapping).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startsAMatchingWorkflowWithTheSubjectAsEntityAndTheMappedContext() {
        when(workflowRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(workflow("fulfillment",
                triggering("order-created", "OrderCreatedEvent",
                        Map.of("orderId", "$.subjectId", "customer", "$.payload.customerName")))));

        listener.on(ORDER_CREATED);

        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(startUseCase).startTriggered(eq(ORG), eq("fulfillment"), eq("order-created"), eq("order-1"), eq("order"), context.capture(),
                eq(List.of()));
        assertThat(context.getValue()).containsEntry("orderId", "order-1").containsEntry("customer", "ACME");
    }

    /** Phase 3: the event's parameters need no mapping of their own; one the mapping names is its override. */
    @Test
    @SuppressWarnings("unchecked")
    void takesTheEventsParametersByName_unlessTheMappingNamesThem() {
        List<OccurredParameter> parameters = List.of(
                new OccurredParameter(OccurredParameter.SUBJECT, EventParameterType.ENTITY_REF, "order", "order-1"),
                new OccurredParameter("orderNumber", EventParameterType.STRING, null, "ORD-1"),
                new OccurredParameter("customerName", EventParameterType.STRING, null, "ACME"));
        when(workflowRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(workflow("fulfillment",
                triggering("order-created", "OrderCreatedEvent", Map.of("customerName", "$.subjectType")))));

        listener.on(new DefinedEventOccurred(ORG, "OrderCreatedEvent", "order", "order-1", Map.of(),
                Instant.parse("2026-10-08T10:00:00Z"), parameters));

        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(startUseCase).startTriggered(eq(ORG), eq("fulfillment"), eq("order-created"), eq("order-1"), eq("order"), context.capture(),
                eq(parameters));
        assertThat(context.getValue()).containsExactly(Map.entry("customerName", "order"), Map.entry("orderNumber", "ORD-1"));
    }

    @Test
    void ignoresOtherEventsOtherStartTypesAndWorkflowsWithoutStartEvents() {
        Workflow manual = workflow("manual", StartEvent.builder().id("by-hand")
                .startType(WorkflowStartConditionType.ROLE_DEFINITION).eventType("OrderCreatedEvent").build());
        Workflow other = workflow("other", triggering("confirmed", "OrderConfirmedEvent", null));
        Workflow bare = Workflow.builder().orgKey(ORG).id("bare").name("bare").startEvents(null).build();
        when(workflowRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(manual, other, bare));

        listener.on(ORDER_CREATED);

        verifyNoInteractions(startUseCase);
    }

    @Test
    void oneWorkflowFailingDoesNotStopTheOthers() {
        when(workflowRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(
                workflow("broken", triggering("start", "OrderCreatedEvent", Map.of("x", "not-a-path"))),
                workflow("failing", triggering("start", "OrderCreatedEvent", null)),
                workflow("healthy", triggering("start", "OrderCreatedEvent", null))));
        when(startUseCase.startTriggered(eq(ORG), eq("failing"), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));
        when(startUseCase.startTriggered(eq(ORG), eq("healthy"), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        listener.on(ORDER_CREATED);

        verify(startUseCase).startTriggered(eq(ORG), eq("healthy"), eq("start"), eq("order-1"), eq("order"), anyMap(), anyList());
    }

    @Test
    void theMappingDocumentIsTheWholeEvent() {
        assertThat(OccurredEventDocument.of(ORDER_CREATED))
                .containsEntry("orgKey", ORG)
                .containsEntry("eventDefinitionId", "OrderCreatedEvent")
                .containsEntry("subjectType", "order")
                .containsEntry("subjectId", "order-1")
                .containsEntry("payload", Map.of("customerName", "ACME"))
                .containsEntry("occurredAt", "2026-10-08T10:00:00Z");
        assertThat(OccurredEventDocument.of(
                new DefinedEventOccurred(ORG, "E", "order", "1", null, null))).containsEntry("occurredAt", null);
    }

    /** A message's correlation value is mappable, which is how a triggered start learns what it answers. */
    @Test
    void theDocumentOfAMessageCarriesItsCorrelationAndSource() {
        UUID occurrence = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        DefinedEventOccurred message = new DefinedEventOccurred(ORG, "InvoiceRequested", "order", "order-1",
                Map.of(), null, CatalogEventKind.MESSAGE, occurrence, "order-1", source);

        assertThat(OccurredEventDocument.of(message))
                .containsEntry("kind", "MESSAGE")
                .containsEntry("correlationValue", "order-1")
                .containsEntry("occurrenceId", occurrence.toString())
                .containsEntry("sourceWorkflowInstanceId", source.toString());
        assertThat(OccurredEventDocument.of(ORDER_CREATED))
                .containsEntry("kind", "SYSTEM")
                .containsEntry("correlationValue", null)
                .containsEntry("sourceWorkflowInstanceId", null);
    }
}
