package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityLabelAdapterTest {

    private static final String ORG = "org-1";
    private static final UUID ORDER = UUID.randomUUID();

    private EntityAttributeQuery attributeQuery;
    private EntityObjectAccess objectAccess;
    private EntityLabelAdapter adapter;

    @BeforeEach
    void setUp() {
        attributeQuery = mock(EntityAttributeQuery.class);
        objectAccess = mock(EntityObjectAccess.class);
        adapter = new EntityLabelAdapter(attributeQuery, objectAccess);
        when(attributeQuery.titleAttribute(ORG, "order")).thenReturn(Optional.of("orderNumber"));
    }

    @Test
    void namesTheObjectByItsTitleAttribute() {
        when(objectAccess.find(ORG, "order", ORDER)).thenReturn(new EntityObjectView(ORDER, 1, Map.of("orderNumber", "ORD-1001")));

        assertThat(adapter.labelOf(ORG, "order", ORDER.toString())).contains("ORD-1001");
    }

    @Test
    void answersEmptyRatherThanFailing() {
        when(attributeQuery.titleAttribute(ORG, "note")).thenReturn(Optional.empty());
        when(objectAccess.find(ORG, "order", ORDER)).thenThrow(new EntityObjectAccessException.NotFound("order", ORDER));

        assertThat(adapter.labelOf(ORG, "order", ORDER.toString())).isEmpty();
        assertThat(adapter.labelOf(ORG, "order", "not-a-uuid")).isEmpty();
        assertThat(adapter.labelOf(ORG, "order", null)).isEmpty();
        assertThat(adapter.labelOf(ORG, "note", ORDER.toString())).isEmpty();
    }

    @Test
    void aBlankTitleIsNoName() {
        when(objectAccess.find(ORG, "order", ORDER)).thenReturn(new EntityObjectView(ORDER, 1, Map.of("orderNumber", " ")));

        assertThat(adapter.labelOf(ORG, "order", ORDER.toString())).isEmpty();
    }
}
