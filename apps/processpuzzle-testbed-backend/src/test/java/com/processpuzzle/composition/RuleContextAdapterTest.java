package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
import com.processpuzzle.baseentity.api.EntityTypeDescription.AttributeDescription;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleContextAdapterTest {

    private final EntityAttributeQuery entities = mock(EntityAttributeQuery.class);
    private final RuleContextAdapter adapter = new RuleContextAdapter(entities);

    private static AttributeDescription plain(String code, EntityAttributeKind kind, boolean required) {
        return new AttributeDescription(code, kind, required, false, false, null, null, List.of());
    }

    private static AttributeDescription embedded(String code, String type, boolean multi) {
        return new AttributeDescription(code, EntityAttributeKind.REFERENCE, false, multi, true, type, null, List.of());
    }

    @Test
    void anUnknownContextIsEmpty() {
        assertThat(adapter.find("acme", "Nope")).isEmpty();
    }

    @Test
    void theEntityIsFoundByItsRuleContextWithItsAttributesMapped() {
        when(entities.describeByRuleContext("acme", "Order")).thenReturn(Optional.of(new EntityTypeDescription("order", "Order",
            List.of(plain("total", EntityAttributeKind.NUMBER, true),
                new AttributeDescription("weight", EntityAttributeKind.TEXT, false, false, false, null, "QUANTITY", List.of("kg"))))));

        RuleContext context = adapter.find("acme", "Order").orElseThrow();

        assertThat(context.name()).isEqualTo("Order");
        assertThat(context.attributes()).extracting(ContextAttribute::name, ContextAttribute::type, ContextAttribute::required)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("total", "NUMBER", true),
                org.assertj.core.groups.Tuple.tuple("weight", "TEXT", false));
        assertThat(context.attributes().get(1).measure()).isEqualTo("QUANTITY");
        assertThat(context.attributes().get(1).measureOptions()).containsExactly("kg");
    }

    @Test
    void anEmbeddedComponentIsExpandedInPlace() {
        when(entities.describeByRuleContext("acme", "Order")).thenReturn(Optional.of(new EntityTypeDescription("order", "Order",
            List.of(embedded("lineItems", "order-line", true)))));
        when(entities.describe("acme", "order-line", Channel.RELEASED)).thenReturn(Optional.of(new EntityTypeDescription(
            "order-line", "Order line", List.of(plain("amount", EntityAttributeKind.NUMBER, false)))));

        ContextAttribute lines = adapter.find("acme", "Order").orElseThrow().attributes().get(0);

        assertThat(lines.embedded()).isTrue();
        assertThat(lines.multiValued()).isTrue();
        assertThat(lines.children()).extracting(ContextAttribute::name).containsExactly("amount");
    }

    @Test
    void aComponentOnlyDraftedIsReadFromTheDraft() {
        when(entities.describeByRuleContext("acme", "Order")).thenReturn(Optional.of(new EntityTypeDescription("order", "Order",
            List.of(embedded("lineItems", "order-line", true)))));
        when(entities.describe("acme", "order-line", Channel.DRAFT)).thenReturn(Optional.of(new EntityTypeDescription(
            "order-line", "Order line", List.of(plain("amount", EntityAttributeKind.NUMBER, false)))));

        assertThat(adapter.find("acme", "Order").orElseThrow().attributes().get(0).children()).hasSize(1);
    }

    @Test
    void aLinkIsNotExpandedAndAnUnresolvableComponentHasNoChildren() {
        when(entities.describeByRuleContext("acme", "Order")).thenReturn(Optional.of(new EntityTypeDescription("order", "Order",
            List.of(new AttributeDescription("customer", EntityAttributeKind.REFERENCE, false, false, false, "customer", null, List.of()),
                embedded("ghost", "ghost", false), embedded("untyped", null, false)))));

        List<ContextAttribute> attributes = adapter.find("acme", "Order").orElseThrow().attributes();

        assertThat(attributes).allSatisfy(attribute -> assertThat(attribute.children()).isEmpty());
    }

    @Test
    void anEntityThatEmbedsItselfIsExpandedNoFurtherThanItsOwnType() {
        EntityTypeDescription tree = new EntityTypeDescription("node", "Node", List.of(embedded("children", "node", true)));
        when(entities.describeByRuleContext("acme", "Node")).thenReturn(Optional.of(tree));
        when(entities.describe("acme", "node", Channel.RELEASED)).thenReturn(Optional.of(tree));

        assertThat(adapter.find("acme", "Node").orElseThrow().attributes().get(0).children()).isEmpty();
    }

    @Test
    void expansionStopsAtAFixedDepth() {
        when(entities.describeByRuleContext("acme", "A")).thenReturn(Optional.of(new EntityTypeDescription("a", "A", List.of(embedded("b", "b", false)))));
        when(entities.describe("acme", "b", Channel.RELEASED)).thenReturn(Optional.of(new EntityTypeDescription("b", "B", List.of(embedded("c", "c", false)))));
        when(entities.describe("acme", "c", Channel.RELEASED)).thenReturn(Optional.of(new EntityTypeDescription("c", "C", List.of(embedded("d", "d", false)))));
        when(entities.describe("acme", "d", Channel.RELEASED)).thenReturn(Optional.of(new EntityTypeDescription("d", "D", List.of(plain("x", EntityAttributeKind.TEXT, false)))));

        ContextAttribute b = adapter.find("acme", "A").orElseThrow().attributes().get(0);
        ContextAttribute c = b.children().get(0);

        assertThat(RuleContextAdapter.MAX_DEPTH).isEqualTo(3);
        assertThat(c.children()).hasSize(1);
        assertThat(c.children().get(0).children()).isEmpty();
    }
}
