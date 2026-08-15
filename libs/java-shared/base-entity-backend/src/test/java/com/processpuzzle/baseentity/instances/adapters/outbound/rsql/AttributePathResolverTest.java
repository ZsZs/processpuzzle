package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributePathResolverTest {

    @Mock
    private EntityDefinitionLookupPort lookupPort;

    private AttributePathResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AttributePathResolver(lookupPort);
    }

    @Test
    void resolve_topLevelAttribute_success() {
        EntityDefinitionView def = new EntityDefinitionView(
                "partner",
                false,
                List.of(new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, false))
        );
        when(lookupPort.findByCode("partner")).thenReturn(Optional.of(def));

        ResolvedAttributePath path = resolver.resolve("partner", "name");

        assertThat(path.valueKind()).isEqualTo(ValueKindView.TEXT);
        assertThat(path.segments()).hasSize(1);
        assertThat(path.segments().get(0).attributeCode()).isEqualTo("name");
        assertThat(path.segments().get(0).array()).isFalse();
    }

    @Test
    void resolve_nestedAttribute_success() {
        EntityDefinitionView addressDef = new EntityDefinitionView(
                "address",
                true,
                List.of(new EntityAttributeView("city", ValueKindView.TEXT, false, false, null, false))
        );

        EntityDefinitionView partnerDef = new EntityDefinitionView(
                "partner",
                false,
                List.of(new EntityAttributeView("billingAddress", ValueKindView.REFERENCE, false, true, "address", false))
        );

        when(lookupPort.findByCode("partner")).thenReturn(Optional.of(partnerDef));
        when(lookupPort.findByCode("address")).thenReturn(Optional.of(addressDef));

        ResolvedAttributePath path = resolver.resolve("partner", "billingAddress.city");

        assertThat(path.valueKind()).isEqualTo(ValueKindView.TEXT);
        assertThat(path.segments()).hasSize(2);
        assertThat(path.segments().get(0).attributeCode()).isEqualTo("billingAddress");
        assertThat(path.segments().get(1).attributeCode()).isEqualTo("city");
    }

    @Test
    void resolve_unknownAttribute_throwsIllegalArgument() {
        EntityDefinitionView def = new EntityDefinitionView("partner", false, List.of());
        when(lookupPort.findByCode("partner")).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> resolver.resolve("partner", "unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
