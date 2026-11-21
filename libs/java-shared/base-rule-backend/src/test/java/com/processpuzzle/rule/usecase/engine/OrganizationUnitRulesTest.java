package com.processpuzzle.rule.usecase.engine;

import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalog.ExtraUnit;
import com.processpuzzle.core.quantity.UnitCatalogProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A rule reads units in the catalog of the organization it belongs to: one that added a pallet can compare weights in
 * pallets, another cannot — on the same engine and the same thread, whose compiled rule is reused between them.
 */
class OrganizationUnitRulesTest {

    private static final String EXPRESSION = "qty(entity.load).gt(qty('900 kg'))";
    private static final Map<String, Object> ENTITY = Map.of("load", Map.of("amount", "2", "unit", "pallet"));

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        UnitCatalog acme = UnitCatalog.standard().withUnits(List.of(new ExtraUnit("MASS", "pallet", "plt", "Pallet", new BigDecimal("500"), BigDecimal.ONE)));
        UnitCatalogProvider catalogs = orgKey -> "acme".equals(orgKey) ? acme : UnitCatalog.standard();
        engine = new RuleEngine(catalogs);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    @Test
    void anOrganizationUnitIsKnownToThatOrganizationsRulesOnly() {
        assertThat(engine.evaluate(RuleKey.of("acme", "heavy"), EXPRESSION, ENTITY)).as("2 pallets = 1000 kg").isTrue();

        assertThatThrownBy(() -> engine.evaluate(RuleKey.of("other", "heavy"), EXPRESSION, ENTITY)).hasMessageContaining("pallet");
        assertThat(engine.evaluate(RuleKey.of("acme", "heavy"), EXPRESSION, ENTITY)).as("and back again").isTrue();
    }

    @Test
    void aRegisteredRuleReadsTheCatalogOfItsOrganization() {
        RuleKey acme = RuleKey.of("acme", "registered");
        engine.registerRule(acme, "qty(entity.load).eq(qty('1000 kg'))");

        assertThat(engine.evaluate(acme, ENTITY)).isTrue();
    }

    @Test
    void anEngineWithoutOrganizationUnitsKnowsTheStandardOnes() {
        try (RuleEngine standard = new RuleEngine()) {
            assertThat(standard.evaluate(RuleKey.of("acme", "plain"), "qty('1 t').eq(qty('1000 kg'))", Map.of())).isTrue();
            assertThatThrownBy(() -> standard.evaluate(RuleKey.of("acme", "heavy"), EXPRESSION, ENTITY)).hasMessageContaining("pallet");
        }
    }
}
