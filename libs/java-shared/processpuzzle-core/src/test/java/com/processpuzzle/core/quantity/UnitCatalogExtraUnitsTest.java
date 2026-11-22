package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import com.processpuzzle.core.quantity.UnitCatalog.ExtraUnit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitCatalogExtraUnitsTest {

    private static final UnitCatalog STANDARD = UnitCatalog.standard();

    private static ExtraUnit pallet(String code) {
        return new ExtraUnit("MASS", code, code, "Pallet", new BigDecimal("500"), BigDecimal.ONE);
    }

    @Test
    void anOrganizationUnitConvertsThroughItsKindsBaseUnit() {
        UnitCatalog catalog = STANDARD.withUnits(List.of(pallet("pallet")));

        assertThat(catalog.unit("pallet").kind()).isEqualTo("MASS");
        assertThat(catalog.unitsOf("MASS")).extracting(UnitCatalog.UnitDefinition::code).contains("kg", "pallet");
        assertThat(Quantity.of("2", "pallet").baseAmount(catalog)).isEqualByComparingTo("1000");
        assertThat(Quantity.of("1", "pallet").convertTo("kg", catalog).amount()).isEqualByComparingTo("500");
    }

    @Test
    void theStandardCatalogIsLeftAsItWas() {
        STANDARD.withUnits(List.of(pallet("pallet")));

        assertThat(STANDARD.findUnit("pallet")).isEmpty();
    }

    @Test
    void noExtraUnitsIsTheSameCatalog() {
        assertThat(STANDARD.withUnits(List.of())).isSameAs(STANDARD);
    }

    @Test
    void refusesACodeTheCatalogHasAndAKindItDoesNot() {
        assertThatThrownBy(() -> STANDARD.withUnits(List.of(pallet("kg")))).isInstanceOfSatisfying(FundamentalTypeException.class, e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_CATALOG));
        assertThatThrownBy(() -> STANDARD.withUnits(List.of(new ExtraUnit("NOPE", "x", "x", "X", BigDecimal.ONE, BigDecimal.ONE))))
            .isInstanceOfSatisfying(FundamentalTypeException.class, e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_CATALOG));
    }
}
