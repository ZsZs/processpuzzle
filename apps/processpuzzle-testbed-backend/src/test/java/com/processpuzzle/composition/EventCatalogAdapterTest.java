package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.usecase.DeclaredParameter;
import com.processpuzzle.event.usecase.EventStateChange;
import com.processpuzzle.event.usecase.FindEventDefinition;
import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.EventParameterType;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogStateChange;
import java.util.List;
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
        when(find.stateChangeOf("org-1", "OrderCancelledEvent")).thenReturn(Optional.of(new EventStateChange("order", "CANCELLED")));
        EventCatalogAdapter adapter = new EventCatalogAdapter(find);

        assertThat(adapter.stateChangeOf("org-1", "OrderCancelledEvent")).contains(new CatalogStateChange("order", "CANCELLED"));
        assertThat(adapter.stateChangeOf("org-1", "Nope")).isEmpty();
    }

    @Test
    void readsTheParametersOfTheDraftAndOfTheReleasedDefinition() {
        FindEventDefinition find = mock(FindEventDefinition.class);
        when(find.parametersOf("org-1", "InvoiceIssued", Channel.RELEASED)).thenReturn(List.of(
                new DeclaredParameter("order", EventParameterType.ENTITY_REF, "order", true)));
        when(find.parametersOf("org-1", "InvoiceIssued", Channel.DRAFT)).thenReturn(List.of(
                new DeclaredParameter("invoiceNumber", EventParameterType.STRING, null, false)));
        EventCatalogAdapter adapter = new EventCatalogAdapter(find);

        assertThat(adapter.releasedParametersOf("org-1", "InvoiceIssued"))
                .containsExactly(new CatalogParameter("order", EventParameterType.ENTITY_REF, "order", true));
        assertThat(adapter.draftParametersOf("org-1", "InvoiceIssued"))
                .containsExactly(new CatalogParameter("invoiceNumber", EventParameterType.STRING, null, false));
    }
}
