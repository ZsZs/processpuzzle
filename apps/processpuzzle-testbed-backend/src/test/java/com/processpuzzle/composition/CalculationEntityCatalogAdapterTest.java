package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
import com.processpuzzle.baseentity.api.EntityTypeDescription.AttributeDescription;
import com.processpuzzle.calculation.port.EntityCatalogPort.Attribute;
import com.processpuzzle.calculation.port.EntityCatalogPort.EntityType;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalculationEntityCatalogAdapterTest {

    private final EntityAttributeQuery entities = mock(EntityAttributeQuery.class);
    private final CalculationEntityCatalogAdapter adapter = new CalculationEntityCatalogAdapter(entities);

    private static final EntityTypeDescription ORDER = new EntityTypeDescription("order", "Order", false, List.of(
        new AttributeDescription("total", EntityAttributeKind.NUMBER, true, false, false, null, null, List.of()),
        new AttributeDescription("lineItems", EntityAttributeKind.REFERENCE, false, true, true, "order-line", null, List.of()),
        new AttributeDescription("weight", EntityAttributeKind.TEXT, false, false, false, null, "QUANTITY", List.of("kg"))));

    @Test
    void everyEntityTypeIsListedWithItsAttributesMapped() {
        when(entities.describeAll("acme")).thenReturn(List.of(ORDER, new EntityTypeDescription("order-line", "Order Line", true, List.of())));

        List<EntityType> all = adapter.all("acme");

        assertThat(all).extracting(EntityType::code, EntityType::name, EntityType::embedded)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("order", "Order", false), org.assertj.core.groups.Tuple.tuple("order-line", "Order Line", true));
        assertThat(all.get(0).attributes()).containsExactly(
            new Attribute("total", "NUMBER", true, false, false, null, null),
            new Attribute("lineItems", "REFERENCE", false, true, true, "order-line", null),
            new Attribute("weight", "TEXT", false, false, false, null, "QUANTITY"));
    }

    @Test
    void aTypeIsFoundInTheReleasedDefinitionsFirstThenInTheDrafts() {
        when(entities.describe("acme", "order", Channel.RELEASED)).thenReturn(Optional.of(ORDER));
        when(entities.describe("acme", "draft-only", Channel.RELEASED)).thenReturn(Optional.empty());
        when(entities.describe("acme", "draft-only", Channel.DRAFT)).thenReturn(Optional.of(new EntityTypeDescription("draft-only", "Draft", List.of())));

        assertThat(adapter.find("acme", "order")).get().extracting(EntityType::code).isEqualTo("order");
        assertThat(adapter.find("acme", "draft-only")).get().extracting(EntityType::code).isEqualTo("draft-only");
        assertThat(adapter.find("acme", "nope")).isEmpty();
    }

    @Test
    void anOrganizationWithoutEntitiesHasNoTypes() {
        assertThat(adapter.all("acme")).isEmpty();
    }
}
