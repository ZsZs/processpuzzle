package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.core.definition.Channel;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubjectAttributeAdapterTest {

    private final EntityAttributeQuery query = mock(EntityAttributeQuery.class);
    private final SubjectAttributeAdapter adapter = new SubjectAttributeAdapter(query);

    @Test
    void answersFromTheEntityTypesDraft() {
        when(query.entityTypeExists("org-1", "order", Channel.DRAFT)).thenReturn(true);
        when(query.attributeKind("org-1", "order", "orderNumber", Channel.DRAFT)).thenReturn(Optional.of(EntityAttributeKind.values()[0]));

        assertThat(adapter.hasAttribute("org-1", "order", "orderNumber")).contains(true);
        assertThat(adapter.hasAttribute("org-1", "order", "orderNo")).contains(false);
    }

    // An entity type it does not know is not an answer: the check is skipped rather than failed.
    @Test
    void cannotTellForAnUnknownEntityType() {
        assertThat(adapter.hasAttribute("org-1", "invoice", "number")).isEmpty();
    }
}
