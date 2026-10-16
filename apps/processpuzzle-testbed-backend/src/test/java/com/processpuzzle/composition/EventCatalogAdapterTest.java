package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventCatalogAdapterTest {

    @Test
    void delegatesToTheEventCatalog() {
        FindEventDefinition find = mock(FindEventDefinition.class);
        when(find.exists("org-1", "OrderCreatedEvent")).thenReturn(true);
        EventCatalogAdapter adapter = new EventCatalogAdapter(find);

        assertThat(adapter.exists("org-1", "OrderCreatedEvent")).isTrue();
        assertThat(adapter.exists("org-1", "Nope")).isFalse();
    }

    @Test
    void asksTheEventCatalogForTheKind() {
        FindEventDefinition find = mock(FindEventDefinition.class);
        when(find.kindOf("org-1", "InvoiceIssued")).thenReturn(Optional.of(CatalogEventKind.MESSAGE));
        when(find.kindOf("org-1", "Nope")).thenReturn(Optional.empty());
        EventCatalogAdapter adapter = new EventCatalogAdapter(find);

        assertThat(adapter.kindOf("org-1", "InvoiceIssued")).contains(CatalogEventKind.MESSAGE);
        assertThat(adapter.kindOf("org-1", "Nope")).isEmpty();
    }

    @Test
    void readsTheStateChangeASystemEventStandsFor() {
        FindEventDefinition find = mock(FindEventDefinition.class);
        when(find.draft("org-1", "OrderCancelledEvent")).thenReturn(Optional.of(EventDefinition.builder().id("OrderCancelledEvent")
                .kind(EventKind.SYSTEM).subjectType("order").action(PlatformEventAction.STATE_CHANGED).state("CANCELLED").build()));
        when(find.draft("org-1", "OrderCreatedEvent")).thenReturn(Optional.of(EventDefinition.builder().id("OrderCreatedEvent")
                .kind(EventKind.SYSTEM).subjectType("order").action(PlatformEventAction.CREATED).build()));
        when(find.draft("org-1", "InvoiceIssued")).thenReturn(Optional.of(EventDefinition.builder().id("InvoiceIssued")
                .kind(EventKind.MESSAGE).build()));
        EventCatalogAdapter adapter = new EventCatalogAdapter(find);

        assertThat(adapter.stateChangeOf("org-1", "OrderCancelledEvent")).contains(new CatalogStateChange("order", "CANCELLED"));
        assertThat(adapter.stateChangeOf("org-1", "OrderCreatedEvent")).isEmpty();
        assertThat(adapter.stateChangeOf("org-1", "InvoiceIssued")).isEmpty();
        assertThat(adapter.stateChangeOf("org-1", "Nope")).isEmpty();
    }
}
