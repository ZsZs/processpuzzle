package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.DefinedEventOccurred;
import com.processpuzzle.shared.event.EventThrown;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ThrownEventCatalogListenerTest {

    private static final String ORG = "org-1";
    private static final Instant WHEN = Instant.parse("2026-10-08T10:00:00Z");
    private static final UUID OCCURRENCE = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();

    private FindEventDefinition find;
    private ApplicationEventPublisher publisher;
    private ThrownEventCatalogListener listener;

    @BeforeEach
    void setUp() {
        find = mock(FindEventDefinition.class);
        publisher = mock(ApplicationEventPublisher.class);
        listener = new ThrownEventCatalogListener(find, publisher);
    }

    private void catalog(String id, EventKind kind) {
        when(find.lookup(ORG, id)).thenReturn(Optional.of(
                EventDefinition.builder().orgKey(ORG).id(id).name(id).kind(kind).build()));
    }

    private static EventThrown thrown(String id) {
        return new EventThrown(ORG, id, OCCURRENCE, "order", "42", "42", Map.of("invoiceNumber", "I-1"), SOURCE,
                "invoicing-workflow", WHEN);
    }

    @Test
    void republishesAMessageWithItsKindCorrelationAndOccurrence() {
        catalog("InvoiceIssued", EventKind.MESSAGE);

        listener.on(thrown("InvoiceIssued"));

        ArgumentCaptor<DefinedEventOccurred> captor = ArgumentCaptor.forClass(DefinedEventOccurred.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new DefinedEventOccurred(ORG, "InvoiceIssued", "order", "42",
                Map.of("invoiceNumber", "I-1"), WHEN, CatalogEventKind.MESSAGE, OCCURRENCE, "42", SOURCE));
    }

    @Test
    void republishesASignal() {
        catalog("StockReplenished", EventKind.SIGNAL);

        listener.on(thrown("StockReplenished"));

        ArgumentCaptor<DefinedEventOccurred> captor = ArgumentCaptor.forClass(DefinedEventOccurred.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(CatalogEventKind.SIGNAL);
    }

    @Test
    void dropsAThrowOfAnUndefinedEvent() {
        when(find.lookup(ORG, "Gone")).thenReturn(Optional.empty());

        listener.on(thrown("Gone"));

        verifyNoInteractions(publisher);
    }

    @Test
    void dropsAThrowOfASystemEvent() {
        catalog("OrderCreatedEvent", EventKind.SYSTEM);

        listener.on(thrown("OrderCreatedEvent"));

        verifyNoInteractions(publisher);
    }
}
