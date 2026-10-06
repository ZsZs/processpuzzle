package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityAttributeQueryAdapterTest {

    private static final String ORG = "acme";

    @Mock
    private EntityDefinitionLookupPort definitionLookupPort;

    @InjectMocks
    private EntityAttributeQueryAdapter adapter;

    private static EntityAttributeView attribute(String code, ValueKindView kind) {
        return new EntityAttributeView(code, kind, false, false, null, false);
    }

    @Test
    void attributeKind_declaredAttribute_isMapped() {
        when(definitionLookupPort.findByCode(ORG, "order")).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("status", ValueKindView.ENUM)))));

        assertThat(adapter.attributeKind(ORG, "order", "status")).contains(EntityAttributeKind.ENUM);
    }

    @Test
    void attributeKind_unknownAttribute_isEmpty() {
        when(definitionLookupPort.findByCode(ORG, "order")).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("status", ValueKindView.ENUM)))));

        assertThat(adapter.attributeKind(ORG, "order", "stauts")).isEmpty();
    }

    @Test
    void attributeKind_unknownEntityType_isEmpty() {
        when(definitionLookupPort.findByCode(ORG, "nope")).thenReturn(Optional.empty());

        assertThat(adapter.attributeKind(ORG, "nope", "status")).isEmpty();
    }

    /**
     * The adapter maps by {@code valueOf(name())}. This is what would fail — loudly, here — if a
     * kind were added to one enum and not the other.
     */
    @ParameterizedTest
    @EnumSource(ValueKindView.class)
    void attributeKind_everyValueKind_hasACounterpart(ValueKindView kind) {
        when(definitionLookupPort.findByCode(ORG, "order")).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("a", kind)))));

        assertThat(adapter.attributeKind(ORG, "order", "a"))
            .contains(EntityAttributeKind.valueOf(kind.name()));
    }

    @Test
    void entityTypeExists_reflectsTheLookup() {
        when(definitionLookupPort.findByCode(ORG, "order")).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of())));
        when(definitionLookupPort.findByCode(ORG, "nope")).thenReturn(Optional.empty());

        assertThat(adapter.entityTypeExists(ORG, "order")).isTrue();
        assertThat(adapter.entityTypeExists(ORG, "nope")).isFalse();
    }
}
