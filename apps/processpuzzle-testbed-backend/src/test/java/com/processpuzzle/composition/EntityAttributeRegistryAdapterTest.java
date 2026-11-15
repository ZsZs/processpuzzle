package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityAttributeRegistryAdapterTest {

    private final EntityAttributeQuery attributeQuery = mock(EntityAttributeQuery.class);
    private final EntityAttributeRegistryAdapter adapter = new EntityAttributeRegistryAdapter(attributeQuery);

    @Test
    void anAttributeThatTheEntityTypeHasIsReported() {
        when(attributeQuery.entityTypeExists("org", "Result")).thenReturn(true);
        when(attributeQuery.attributeKind(any(), any(), any())).thenReturn(Optional.of(mock(EntityAttributeKind.class)));

        assertThat(adapter.hasAttribute("org", "Result", "race")).contains(true);
    }

    @Test
    void anAttributeThatTheEntityTypeLacksIsReported() {
        when(attributeQuery.entityTypeExists("org", "Result")).thenReturn(true);
        when(attributeQuery.attributeKind(any(), any(), any())).thenReturn(Optional.empty());

        assertThat(adapter.hasAttribute("org", "Result", "race")).contains(false);
    }

    @Test
    void anUnknownEntityTypeMakesNoClaim() {
        when(attributeQuery.entityTypeExists("org", "Missing")).thenReturn(false);

        assertThat(adapter.hasAttribute("org", "Missing", "race")).isEmpty();
    }
}
