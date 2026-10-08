package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.usecase.FindEventDefinition;
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
}
