package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityDefinitionLookupAdapterTest {

    @Mock
    private EntityDefinitionRepository repository;

    private EntityDefinitionLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EntityDefinitionLookupAdapter(repository);
    }

    @Test
    void findByCode_returnsEmpty_whenNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        Optional<EntityDefinitionView> result = adapter.findByCode("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_mapsDefinitionAndAttributesCorrectly() {
        BaseEntityAttribute nameAttr = BaseEntityAttribute.builder()
                .code("name")
                .valueKind(ValueKind.TEXT)
                .formControlType(FormControlType.TEXT)
                .isMultiValued(false)
                .required(true)
                .linkedEntityType(null)
                .build();

        BaseEntityAttribute addressAttr = BaseEntityAttribute.builder()
                .code("addresses")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.EMBEDDED_COMPONENTS)
                .isMultiValued(true)
                .required(false)
                .linkedEntityType("address")
                .build();

        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("partner")
                .isEmbedded(false)
                .attributes(List.of(nameAttr, addressAttr))
                .build();

        when(repository.findByCode("partner")).thenReturn(Optional.of(definition));

        Optional<EntityDefinitionView> result = adapter.findByCode("partner");

        assertThat(result).isPresent();
        EntityDefinitionView view = result.get();
        assertThat(view.code()).isEqualTo("partner");
        assertThat(view.embedded()).isFalse();
        assertThat(view.attributes()).hasSize(2);

        EntityAttributeView viewAttr1 = view.attributes().get(0);
        assertThat(viewAttr1.code()).isEqualTo("name");
        assertThat(viewAttr1.valueKind()).isEqualTo(ValueKindView.TEXT);
        assertThat(viewAttr1.multiValued()).isFalse();
        assertThat(viewAttr1.embeddedComponent()).isFalse();
        assertThat(viewAttr1.required()).isTrue();
        assertThat(viewAttr1.linkedEntityType()).isNull();

        EntityAttributeView viewAttr2 = view.attributes().get(1);
        assertThat(viewAttr2.code()).isEqualTo("addresses");
        assertThat(viewAttr2.valueKind()).isEqualTo(ValueKindView.REFERENCE);
        assertThat(viewAttr2.multiValued()).isTrue();
        assertThat(viewAttr2.embeddedComponent()).isTrue();
        assertThat(viewAttr2.required()).isFalse();
        assertThat(viewAttr2.linkedEntityType()).isEqualTo("address");
    }
}
