package com.processpuzzle.calculation.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calculation.port.EntityCatalogPort.Attribute;
import com.processpuzzle.calculation.port.EntityCatalogPort.EntityType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDescriptionTest {

    @Test
    void everyEntityIsOneLineWithItsAttributesTheirTypesAndRequiredness() {
        String text = CatalogDescription.of(Shop.CATALOG.all("acme"));

        assertThat(text)
            .contains("- customer (Customer): name: TEXT (required); tier: TEXT")
            .contains("- order (Order): reference: TEXT (required); total: NUMBER; lineItems: list of embedded order-line")
            .contains("- order-line (Order Line) [embedded component: it exists only inside another entity's objects]: "
                + "quantity: QUANTITY (required); unitPrice: MONEY; product: link to product");
    }

    @Test
    void aNameThatIsTheCodeIsNotRepeated() {
        assertThat(CatalogDescription.of(List.of(new EntityType("order", "order", false, List.of(new Attribute("total", "NUMBER", false, false, false, null, null))))))
            .contains("- order: total: NUMBER").doesNotContain("(order)");
    }

    @Test
    void noEntitiesSaysTheyAreNotKnown() {
        assertThat(CatalogDescription.of(List.of())).contains("not known");
    }

    @Test
    void aLongCatalogIsCutAndTheModelIsToldHowMuchIsMissing() {
        List<EntityType> many = new ArrayList<>();
        for (int i = 0; i < CatalogDescription.MAX_ENTITIES + 5; i++) {
            many.add(new EntityType("e" + i, "e" + i, false, List.of()));
        }

        String text = CatalogDescription.of(many);

        assertThat(text).contains("- e0:").contains("- e" + (CatalogDescription.MAX_ENTITIES - 1) + ":")
            .doesNotContain("- e" + CatalogDescription.MAX_ENTITIES + ":").contains("(5 more entity types are not listed.)");
    }
}
