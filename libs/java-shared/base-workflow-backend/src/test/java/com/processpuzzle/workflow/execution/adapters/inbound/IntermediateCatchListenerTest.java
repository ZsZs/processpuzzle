package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.usecases.inbound.OccurCatchEventUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class IntermediateCatchListenerTest {

    private static final String ORG = "acme";
    private static final Instant WHEN = Instant.parse("2026-10-08T10:00:00Z");

    private EventInstanceRepository events;
    private WorkflowInstanceRepository instances;
    private OccurCatchEventUseCase occur;
    private IntermediateCatchListener listener;

    @BeforeEach
    void setUp() {
        events = mock(EventInstanceRepository.class);
        instances = mock(WorkflowInstanceRepository.class);
        occur = mock(OccurCatchEventUseCase.class);
        when(occur.occur(any(), any(), any())).thenReturn(true);
        listener = new IntermediateCatchListener(events, instances, occur);
    }

    // ---------------------------------------------------------------- SYSTEM

    @Test
    void aSystemEventReachesEveryCatchWaitingForItsSubjectOfTheRightType() {
        EventInstance order = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        EventInstance untyped = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        EventInstance partner = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        waitingFor("OrderConfirmedEvent", "42", order, untyped, partner);
        instanceOf(order, "order");
        instanceOf(untyped, null);
        instanceOf(partner, "partner");
        DefinedEventOccurred system = new DefinedEventOccurred(ORG, "OrderConfirmedEvent", "order", "42", Map.of(), WHEN);

        listener.on(system);

        verify(occur).occur(ORG, order.getId(), system);
        verify(occur).occur(ORG, untyped.getId(), system);
        verify(occur, never()).occur(ORG, partner.getId(), system);
    }

    /** A publication stored before kinds existed has none, and is delivered as the SYSTEM event it was. */
    @Test
    void anEventWithoutKindIsSystem() {
        EventInstance order = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        waitingFor("OrderConfirmedEvent", "42", order);
        DefinedEventOccurred legacy = new DefinedEventOccurred(ORG, "OrderConfirmedEvent", null, "42", Map.of(), WHEN,
                null, null, null, null);

        listener.on(legacy);

        verify(occur).occur(ORG, order.getId(), legacy);
    }

    @Test
    void aSystemEventDeliversToEveryoneBeforeRethrowingTheFirstFailure() {
        EventInstance failing = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        EventInstance fine = waiting("OrderConfirmedEvent", "42", UUID.randomUUID());
        waitingFor("OrderConfirmedEvent", "42", failing, fine);
        DefinedEventOccurred system = new DefinedEventOccurred(ORG, "OrderConfirmedEvent", null, "42", Map.of(), WHEN);
        when(occur.occur(ORG, failing.getId(), system)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> listener.on(system)).hasMessage("boom");
        verify(occur).occur(ORG, fine.getId(), system);
    }

    // ---------------------------------------------------------------- MESSAGE

    @Test
    void aMessageReachesOnlyTheLongestWaitingRecipientOtherThanItsSender() {
        UUID sender = UUID.randomUUID();
        EventInstance own = waiting("InvoiceIssued", "42", sender);
        EventInstance oldest = waiting("InvoiceIssued", "42", UUID.randomUUID());
        EventInstance newer = waiting("InvoiceIssued", "42", UUID.randomUUID());
        waitingFor("InvoiceIssued", "42", own, oldest, newer);
        DefinedEventOccurred message = message("InvoiceIssued", "42", sender);

        listener.on(message);

        verify(occur).occur(ORG, oldest.getId(), message);
        verify(occur, never()).occur(ORG, own.getId(), message);
        verify(occur, never()).occur(ORG, newer.getId(), message);
    }

    @Test
    void aMessageThatLosesTheRaceForOneRecipientTriesTheNext() {
        EventInstance taken = waiting("InvoiceIssued", "42", UUID.randomUUID());
        EventInstance next = waiting("InvoiceIssued", "42", UUID.randomUUID());
        waitingFor("InvoiceIssued", "42", taken, next);
        DefinedEventOccurred message = message("InvoiceIssued", "42", UUID.randomUUID());
        when(occur.occur(ORG, taken.getId(), message)).thenThrow(new OptimisticLockingFailureException("raced"));

        listener.on(message);

        verify(occur).occur(ORG, next.getId(), message);
    }

    @Test
    void aMessageAlreadyDeliveredIsNotDeliveredAgain() {
        DefinedEventOccurred message = message("InvoiceIssued", "42", UUID.randomUUID());
        when(events.existsByOrgKeyAndOccurrenceIdAndDirection(ORG, message.occurrenceId(), EventDirection.CATCH))
                .thenReturn(true);

        listener.on(message);

        verifyNoInteractions(occur);
    }

    @Test
    void aMessageNobodyWaitsForIsDropped() {
        waitingFor("InvoiceIssued", "42");

        listener.on(message("InvoiceIssued", "42", UUID.randomUUID()));
        listener.on(message("InvoiceIssued", null, UUID.randomUUID()));

        verifyNoInteractions(occur);
    }

    // ---------------------------------------------------------------- SIGNAL

    @Test
    void aSignalReachesEveryWaitingCatch() {
        EventInstance first = waiting("StockReplenished", "1", UUID.randomUUID());
        EventInstance second = waiting("StockReplenished", "2", UUID.randomUUID());
        when(events.findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusOrderByWaitingSinceAscIdAsc(
                ORG, "StockReplenished", EventDirection.CATCH, EventInstanceStatus.WAITING)).thenReturn(List.of(first, second));
        DefinedEventOccurred signal = new DefinedEventOccurred(ORG, "StockReplenished", null, null, Map.of(), WHEN,
                CatalogEventKind.SIGNAL, UUID.randomUUID(), null, UUID.randomUUID());

        listener.on(signal);

        verify(occur).occur(ORG, first.getId(), signal);
        verify(occur).occur(ORG, second.getId(), signal);
    }

    // ---------------------------------------------------------------- fixtures

    private static EventInstance waiting(String definition, String correlationValue, UUID instanceId) {
        return EventInstance.builder().id(UUID.randomUUID()).orgKey(ORG).workflowInstanceId(instanceId)
                .eventUseId("e").eventDefinitionId(definition).direction(EventDirection.CATCH)
                .status(EventInstanceStatus.WAITING).correlationValue(correlationValue).build();
    }

    private void waitingFor(String definition, String correlationValue, EventInstance... catches) {
        when(events.findByOrgKeyAndEventDefinitionIdAndDirectionAndStatusAndCorrelationValueOrderByWaitingSinceAscIdAsc(
                eq(ORG), eq(definition), eq(EventDirection.CATCH), eq(EventInstanceStatus.WAITING), eq(correlationValue)))
                .thenReturn(List.of(catches));
    }

    private void instanceOf(EventInstance catchEvent, String entityType) {
        when(instances.findByOrgKeyAndId(ORG, catchEvent.getWorkflowInstanceId())).thenReturn(Optional.of(
                WorkflowInstance.builder().id(catchEvent.getWorkflowInstanceId()).entityType(entityType).build()));
    }

    private static DefinedEventOccurred message(String definition, String correlationValue, UUID source) {
        return new DefinedEventOccurred(ORG, definition, "order", "42", Map.of(), WHEN, CatalogEventKind.MESSAGE,
                UUID.randomUUID(), correlationValue, source);
    }
}
