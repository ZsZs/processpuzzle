package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.Multiplicity;
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

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.processpuzzle.core.tenancy.DataSpaceDirectory> dataSpaces;
    @Mock
    private com.processpuzzle.core.tenancy.DataSpaceDirectory directory;

    private EntityDefinitionLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EntityDefinitionLookupAdapter(repository, dataSpaces);
    }

    private static BaseEntityDefinition shared(String orgKey, String code) {
        return BaseEntityDefinition.builder().orgKey(orgKey).code(code).name(code)
            .sharingWrite(com.processpuzzle.baseentity.definition.domain.SharingWrite.COMMON_ADMIN)
            .sharingVisibility(com.processpuzzle.baseentity.definition.domain.SharingVisibility.PUBLIC).build();
    }

    @Test
    void findByCode_fallsBackToTheCommonsSharedType() {
        when(repository.findByOrgKeyAndCodeAndChannel("club", "asset", Channel.RELEASED)).thenReturn(java.util.Optional.empty());
        when(dataSpaces.getIfAvailable()).thenReturn(directory);
        when(directory.commonOf("club")).thenReturn(java.util.Optional.of("starter-common"));
        when(repository.findByOrgKeyAndCodeAndChannel("starter-common", "asset", Channel.RELEASED))
            .thenReturn(java.util.Optional.of(shared("starter-common", "asset")));

        assertThat(adapter.findByCode("club", "asset")).isPresent();
    }

    @Test
    void findByCode_ignoresACommonTypeThatIsNotShared() {
        when(repository.findByOrgKeyAndCodeAndChannel("club", "internal", Channel.RELEASED)).thenReturn(java.util.Optional.empty());
        when(dataSpaces.getIfAvailable()).thenReturn(directory);
        when(directory.commonOf("club")).thenReturn(java.util.Optional.of("starter-common"));
        when(repository.findByOrgKeyAndCodeAndChannel("starter-common", "internal", Channel.RELEASED))
            .thenReturn(java.util.Optional.of(BaseEntityDefinition.builder().orgKey("starter-common").code("internal").name("i").build()));

        assertThat(adapter.findByCode("club", "internal")).isEmpty();
    }

    @Test
    void sharedFrom_namesTheCommon() {
        when(repository.existsByOrgKeyAndCodeAndChannel("club", "asset", Channel.RELEASED)).thenReturn(false);
        when(dataSpaces.getIfAvailable()).thenReturn(directory);
        when(directory.commonOf("club")).thenReturn(java.util.Optional.of("starter-common"));
        when(repository.findByOrgKeyAndCodeAndChannel("starter-common", "asset", Channel.RELEASED))
            .thenReturn(java.util.Optional.of(shared("starter-common", "asset")));

        assertThat(adapter.sharedFrom("club", "asset")).contains("starter-common");
    }

    @Test
    void sharedFrom_isEmptyWhenTheMemberDefinesTheCodeItself() {
        when(repository.existsByOrgKeyAndCodeAndChannel("club", "asset", Channel.RELEASED)).thenReturn(true);

        assertThat(adapter.sharedFrom("club", "asset")).isEmpty();
    }

    @Test
    void findByCode_returnsEmpty_whenNotFound() {
        when(repository.findByOrgKeyAndCodeAndChannel("acme", "unknown", Channel.RELEASED)).thenReturn(Optional.empty());

        Optional<EntityDefinitionView> result = adapter.findByCode("acme", "unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_mapsDefinitionAndAttributesCorrectly() {
        BaseEntityAttribute nameAttr = BaseEntityAttribute.builder()
                .code("name")
                .valueKind(ValueKind.TEXT)
                .formControlType(FormControlType.TEXT)
                .required(true)
                .isLinkToDetails(true)
                .linkedEntityType(null)
                .build();

        BaseEntityAttribute addressAttr = BaseEntityAttribute.builder()
                .code("addresses")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.EMBEDDED_COMPONENTS)
                .multiplicity(Multiplicity.ZERO_TO_N)
                .required(false)
                .linkedEntityType("address")
                .build();

        BaseEntityDefinition definition = BaseEntityDefinition.builder()
                .code("partner")
                .isEmbedded(false)
                .attributes(List.of(nameAttr, addressAttr))
                .build();

        when(repository.findByOrgKeyAndCodeAndChannel("acme", "partner", Channel.RELEASED)).thenReturn(Optional.of(definition));

        Optional<EntityDefinitionView> result = adapter.findByCode("acme", "partner");

        assertThat(result).isPresent();
        EntityDefinitionView view = result.get();
        assertThat(view.code()).isEqualTo("partner");
        assertThat(view.embedded()).isFalse();
        assertThat(view.attributes()).hasSize(2);
        assertThat(view.titleAttribute()).isEqualTo("name");

        EntityAttributeView viewAttr1 = view.attributes().get(0);
        assertThat(viewAttr1.code()).isEqualTo("name");
        assertThat(viewAttr1.valueKind()).isEqualTo(ValueKindView.TEXT);
        assertThat(viewAttr1.multiValued()).isFalse();
        assertThat(viewAttr1.embeddedComponent()).isFalse();
        assertThat(viewAttr1.required()).isTrue();
        assertThat(viewAttr1.linkedEntityType()).isNull();
        assertThat(viewAttr1.comments()).isFalse();
        assertThat(viewAttr1.address()).isFalse();

        EntityAttributeView viewAttr2 = view.attributes().get(1);
        assertThat(viewAttr2.code()).isEqualTo("addresses");
        assertThat(viewAttr2.valueKind()).isEqualTo(ValueKindView.REFERENCE);
        assertThat(viewAttr2.multiValued()).isTrue();
        assertThat(viewAttr2.embeddedComponent()).isTrue();
        assertThat(viewAttr2.required()).isFalse();
        assertThat(viewAttr2.linkedEntityType()).isEqualTo("address");
    }

    @Test
    void findByCode_flagsACommentsAttribute() {
        BaseEntityAttribute commentsAttr = BaseEntityAttribute.builder()
                .code("comments")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.COMMENTS)
                .multiplicity(Multiplicity.ZERO_TO_N)
                .build();
        BaseEntityDefinition definition = BaseEntityDefinition.builder().code("order").attributes(List.of(commentsAttr)).build();
        when(repository.findByOrgKeyAndCodeAndChannel("acme", "order", Channel.RELEASED)).thenReturn(Optional.of(definition));

        EntityAttributeView view = adapter.findByCode("acme", "order").orElseThrow().attributes().get(0);

        assertThat(view.comments()).isTrue();
        assertThat(view.embeddedComponent()).isFalse();
        assertThat(view.multiValued()).isTrue();
    }

    @Test
    void findByCode_flagsAnAddressAttribute() {
        BaseEntityAttribute addressAttr = BaseEntityAttribute.builder()
                .code("address")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.ADDRESS)
                .build();
        BaseEntityDefinition definition = BaseEntityDefinition.builder().code("customer").attributes(List.of(addressAttr)).build();
        when(repository.findByOrgKeyAndCodeAndChannel("acme", "customer", Channel.RELEASED)).thenReturn(Optional.of(definition));

        EntityAttributeView view = adapter.findByCode("acme", "customer").orElseThrow().attributes().get(0);

        assertThat(view.address()).isTrue();
        assertThat(view.comments()).isFalse();
        assertThat(view.multiValued()).isFalse();
    }

    @Test
    void findByCode_flagsAGeoLocationAttribute() {
        BaseEntityAttribute locationAttr = BaseEntityAttribute.builder()
                .code("location")
                .valueKind(ValueKind.REFERENCE)
                .formControlType(FormControlType.GEO_LOCATION)
                .build();
        BaseEntityDefinition definition = BaseEntityDefinition.builder().code("visit").attributes(List.of(locationAttr)).build();
        when(repository.findByOrgKeyAndCodeAndChannel("acme", "visit", Channel.RELEASED)).thenReturn(Optional.of(definition));

        EntityAttributeView view = adapter.findByCode("acme", "visit").orElseThrow().attributes().get(0);

        assertThat(view.geoLocation()).isTrue();
        assertThat(view.address()).isFalse();
    }
}
