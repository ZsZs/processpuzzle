package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.port.EntityWriteConflict;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculationAdaptersTest {

    private final EntityObjectAccess access = mock(EntityObjectAccess.class);
    private final CalculationEntityAdapter adapter = new CalculationEntityAdapter(access);
    private final UUID id = UUID.randomUUID();

    @Test
    void readsObjectsAndTreatsAnUnknownOrMalformedIdAsAbsent() {
        when(access.find("org", "order", id)).thenReturn(new EntityObjectView(id, 2, Map.of("total", 1)));
        assertThat(adapter.find("org", "order", id.toString())).hasValueSatisfying(record -> {
            assertThat(record.id()).isEqualTo(id.toString());
            assertThat(record.version()).isEqualTo(2);
        });

        UUID missing = UUID.randomUUID();
        when(access.find("org", "order", missing)).thenThrow(new EntityObjectAccessException.NotFound("order", missing));
        assertThat(adapter.find("org", "order", missing.toString())).isEmpty();
        assertThat(adapter.find("org", "order", "not-a-uuid")).isEmpty();
    }

    @Test
    void queriesThroughRsql() {
        when(access.findWhere("org", "finish", "series==s")).thenReturn(List.of(new EntityObjectView(id, 0, Map.of())));
        assertThat(adapter.findWhere("org", "finish", "series==s")).hasSize(1);
    }

    @Test
    void writesAndTranslatesConflicts() {
        adapter.updateAttributes("org", "order", id.toString(), Map.of("total", 1), 3);
        verify(access).updateAttributes("org", "order", id, Map.of("total", 1), 3);

        doThrow(new EntityObjectAccessException.VersionConflict(id, 3, 4)).when(access)
                .updateAttributes(eq("org"), eq("order"), eq(id), any(), anyLong());
        assertThatThrownBy(() -> adapter.updateAttributes("org", "order", id.toString(), Map.of(), 3))
                .isInstanceOf(EntityWriteConflict.class);
        assertThatThrownBy(() -> adapter.updateAttributes("org", "order", "x", Map.of(), 3)).isInstanceOf(EntityWriteConflict.class);

        when(access.upsertByKey("org", "result", Map.of("boat", "A"), Map.of("rank", 1))).thenReturn(true);
        assertThat(adapter.upsertByKey("org", "result", Map.of("boat", "A"), Map.of("rank", 1))).isTrue();
        when(access.upsertByKey(eq("org"), eq("nope"), any(), any())).thenThrow(new EntityObjectAccessException.UnknownType("nope"));
        assertThatThrownBy(() -> adapter.upsertByKey("org", "nope", Map.of(), Map.of())).isInstanceOf(EntityWriteConflict.class);
    }

    @Test
    void typesAttributesByTheirKind() {
        EntityAttributeQuery attributes = mock(EntityAttributeQuery.class);
        CalculationAttributeTypeAdapter types = new CalculationAttributeTypeAdapter(attributes);
        Map<EntityAttributeKind, AttributeType> expected = Map.of(EntityAttributeKind.TEXT, AttributeType.TEXT,
                EntityAttributeKind.ENUM, AttributeType.TEXT, EntityAttributeKind.NUMBER, AttributeType.NUMBER,
                EntityAttributeKind.BOOLEAN, AttributeType.BOOLEAN, EntityAttributeKind.DATE, AttributeType.DATE,
                EntityAttributeKind.DATE_TIME, AttributeType.DATE_TIME, EntityAttributeKind.REFERENCE, AttributeType.OTHER);
        expected.forEach((kind, type) -> {
            when(attributes.attributeKind("org", "e", kind.name())).thenReturn(Optional.of(kind));
            assertThat(types.typeOf("org", "e", kind.name())).contains(type);
        });
        assertThat(types.typeOf("org", "e", "unknown")).isEmpty();
    }

    @Test
    void managesTheAttributesCalculationsStore() {
        FindCalculationDefinition calculations = mock(FindCalculationDefinition.class);
        when(calculations.storedAttributesOf("org", "order")).thenReturn(Set.of("total"));

        assertThat(new CalculationManagedAttributesAdapter(calculations).managedAttributesOf("org", "order")).containsExactly("total");
    }
}
