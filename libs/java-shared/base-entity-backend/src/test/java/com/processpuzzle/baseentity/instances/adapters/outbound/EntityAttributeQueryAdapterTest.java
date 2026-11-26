package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
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
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("status", ValueKindView.ENUM)))));

        assertThat(adapter.attributeKind(ORG, "order", "status")).contains(EntityAttributeKind.ENUM);
    }

    /** A module checking a draft of its own — base-state's validator — asks about the entity type's draft. */
    @Test
    void askingAboutTheDraftReadsTheDraft_notTheReleasedRow() {
        when(definitionLookupPort.findByCode(ORG, "order", Channel.DRAFT)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("status", ValueKindView.ENUM)))));
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.attributeKind(ORG, "order", "status", Channel.DRAFT)).contains(EntityAttributeKind.ENUM);
        assertThat(adapter.entityTypeExists(ORG, "order", Channel.DRAFT)).isTrue();
        assertThat(adapter.entityTypeExists(ORG, "order")).isFalse();
    }

    @Test
    void attributeKind_unknownAttribute_isEmpty() {
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("status", ValueKindView.ENUM)))));

        assertThat(adapter.attributeKind(ORG, "order", "stauts")).isEmpty();
    }

    @Test
    void titleAttribute_isTheDefinitionsLinkToDetailsAttribute() {
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("orderNumber", ValueKindView.TEXT)), "orderNumber")));
        when(definitionLookupPort.findByCode(ORG, "nope", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.titleAttribute(ORG, "order")).contains("orderNumber");
        assertThat(adapter.titleAttribute(ORG, "nope")).isEmpty();
    }

    @Test
    void attributeKind_unknownEntityType_isEmpty() {
        when(definitionLookupPort.findByCode(ORG, "nope", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.attributeKind(ORG, "nope", "status")).isEmpty();
    }

    /**
     * The adapter maps by {@code valueOf(name())}. This is what would fail — loudly, here — if a
     * kind were added to one enum and not the other.
     */
    @ParameterizedTest
    @EnumSource(ValueKindView.class)
    void attributeKind_everyValueKind_hasACounterpart(ValueKindView kind) {
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("a", kind)))));

        assertThat(adapter.attributeKind(ORG, "order", "a"))
            .contains(EntityAttributeKind.valueOf(kind.name()));
    }

    @Test
    void entityTypeExists_reflectsTheLookup() {
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of())));
        when(definitionLookupPort.findByCode(ORG, "nope", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(adapter.entityTypeExists(ORG, "order")).isTrue();
        assertThat(adapter.entityTypeExists(ORG, "nope")).isFalse();
    }

    @Test
    void describe_listsTheAttributesWithTheirKindRequirednessAndMeasure() {
        MeasureView measure = new MeasureView(MeasureView.Kind.QUANTITY, "mass", List.of("kg", "g"), null, null, null);
        when(definitionLookupPort.findByCode(ORG, "order", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(
                new EntityAttributeView("total", ValueKindView.NUMBER, false, false, null, true),
                new EntityAttributeView("lineItems", ValueKindView.REFERENCE, true, true, "order-line", false),
                new EntityAttributeView("weight", ValueKindView.TEXT, false, false, null, false, false, false, false, measure)),
                null, "Order")));

        EntityTypeDescription description = adapter.describe(ORG, "order", Channel.RELEASED).orElseThrow();

        assertThat(description.code()).isEqualTo("order");
        assertThat(description.name()).isEqualTo("Order");
        assertThat(description.attributes()).extracting(EntityTypeDescription.AttributeDescription::code)
            .containsExactly("total", "lineItems", "weight");
        assertThat(description.attributes().get(0).kind()).isEqualTo(EntityAttributeKind.NUMBER);
        assertThat(description.attributes().get(0).required()).isTrue();
        assertThat(description.attributes().get(1).embedded()).isTrue();
        assertThat(description.attributes().get(1).multiValued()).isTrue();
        assertThat(description.attributes().get(1).linkedEntityType()).isEqualTo("order-line");
        assertThat(description.attributes().get(2).measure()).isEqualTo("QUANTITY");
        assertThat(description.attributes().get(2).measureOptions()).containsExactly("kg", "g");
    }

    @Test
    void describe_unknownTypeIsEmpty() {
        assertThat(adapter.describe(ORG, "nope", Channel.RELEASED)).isEmpty();
    }

    @Test
    void measureOptions_fallBackToTheCurrenciesThenTheDefault() {
        when(definitionLookupPort.findByCode(ORG, "price", Channel.RELEASED)).thenReturn(Optional.of(
            new EntityDefinitionView("price", false, List.of(
                new EntityAttributeView("amount", ValueKindView.TEXT, false, false, null, false, false, false, false,
                    new MeasureView(MeasureView.Kind.MONEY, null, null, null, List.of("EUR", "USD"), null)),
                new EntityAttributeView("size", ValueKindView.TEXT, false, false, null, false, false, false, false,
                    new MeasureView(MeasureView.Kind.QUANTITY, "length", null, null, null, null, "m", null, null)),
                new EntityAttributeView("blank", ValueKindView.TEXT, false, false, null, false, false, false, false,
                    new MeasureView(MeasureView.Kind.QUANTITY, "length", null, null, null, null))))));

        var attributes = adapter.describe(ORG, "price", Channel.RELEASED).orElseThrow().attributes();

        assertThat(attributes.get(0).measureOptions()).containsExactly("EUR", "USD");
        assertThat(attributes.get(1).measureOptions()).containsExactly("m");
        assertThat(attributes.get(2).measureOptions()).isEmpty();
    }

    @Test
    void describeByRuleContext_readsTheReleasedTypeFirstThenTheDraft() {
        when(definitionLookupPort.findByRuleContext(ORG, "Order", Channel.RELEASED)).thenReturn(Optional.empty());
        when(definitionLookupPort.findByRuleContext(ORG, "Order", Channel.DRAFT)).thenReturn(Optional.of(
            new EntityDefinitionView("order", false, List.of(attribute("total", ValueKindView.NUMBER)), null, "Order")));

        assertThat(adapter.describeByRuleContext(ORG, "Order")).get()
            .extracting(EntityTypeDescription::code).isEqualTo("order");
        assertThat(adapter.describeByRuleContext(ORG, "Missing")).isEmpty();
    }

    @Test
    void describeAll_listsReleasedTypesAndDraftOnlyOnesSortedByCodeWithTheEmbeddedFlag() {
        when(definitionLookupPort.findAll(ORG, Channel.RELEASED)).thenReturn(List.of(
            new EntityDefinitionView("order", false, List.of(attribute("total", ValueKindView.NUMBER)), null, "Order"),
            new EntityDefinitionView("order-line", true, List.of(), null, "Order Line")));
        when(definitionLookupPort.findAll(ORG, Channel.DRAFT)).thenReturn(List.of(
            new EntityDefinitionView("order", false, List.of(attribute("draftOnly", ValueKindView.TEXT)), null, "Order"),
            new EntityDefinitionView("customer", false, List.of(), null, "Customer")));

        List<EntityTypeDescription> all = adapter.describeAll(ORG);

        assertThat(all).extracting(EntityTypeDescription::code).containsExactly("customer", "order", "order-line");
        assertThat(all.get(1).attributes()).extracting(EntityTypeDescription.AttributeDescription::code).containsExactly("total");
        assertThat(all.get(2).embedded()).isTrue();
        assertThat(all.get(0).embedded()).isFalse();
    }

    @Test
    void describeAll_ofAnOrganizationWithoutDefinitionsIsEmpty() {
        assertThat(adapter.describeAll(ORG)).isEmpty();
    }
}
