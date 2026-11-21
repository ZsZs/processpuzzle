package com.processpuzzle.baseentity.instances.domain;

import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasureValuesTest {

    private static final MeasureView MASS = new MeasureView(Kind.QUANTITY, "MASS", null, null, null, null);
    private static final MeasureView GRAMS = new MeasureView(Kind.QUANTITY, null, List.of("g", "mg"), 2, null, null);
    private static final MeasureView ANY_UNIT = new MeasureView(Kind.QUANTITY, null, null, null, null, null);
    private static final MeasureView PRICE = new MeasureView(Kind.MONEY, null, List.of("EUR", "JPY"), null, List.of("EUR", "JPY"), null);
    private static final MeasureView FLAT = new MeasureView(Kind.DIMENSIONS, null, List.of("cm", "m"), null, null, null);
    private static final MeasureView BOX = new MeasureView(Kind.DIMENSIONS, null, null, 1, null, 3);

    private static Map<String, Object> value(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void aValueThatIsNotAnObject_isReportedAsSuch() {
        assertThat(MeasureValues.problems(MASS, "5 kg")).containsExactly("expected a quantity object");
        assertThat(MeasureValues.problems(PRICE, 5)).containsExactly("expected a money object");
        assertThat(MeasureValues.problems(FLAT, List.of())).containsExactly("expected a dimensions object");
    }

    @Test
    void aValidQuantity_hasNoProblems_whatEverFormItsAmountIsIn() {
        assertThat(MeasureValues.problems(MASS, value("amount", "12.50", "unit", "kg"))).isEmpty();
        assertThat(MeasureValues.problems(MASS, value("amount", 12, "unit", "kg"))).isEmpty();
        assertThat(MeasureValues.problems(MASS, value("amount", 12.5, "unit", "kg"))).isEmpty();
        assertThat(MeasureValues.problems(MASS, value("amount", new java.math.BigDecimal("1E+3"), "unit", "g"))).isEmpty();
        assertThat(MeasureValues.problems(ANY_UNIT, value("amount", "-3", "unit", "degC"))).isEmpty();
    }

    @Test
    void aQuantityWithABadAmount_isReported() {
        assertThat(MeasureValues.problems(MASS, value("amount", "12,5", "unit", "kg"))).containsExactly("amount must be a decimal number");
        assertThat(MeasureValues.problems(MASS, value("unit", "kg"))).containsExactly("amount must be a decimal number");
        assertThat(MeasureValues.problems(MASS, value("amount", Double.NaN, "unit", "kg"))).containsExactly("amount must be a decimal number");
        assertThat(MeasureValues.problems(MASS, value("amount", true, "unit", "kg"))).containsExactly("amount must be a decimal number");
    }

    @Test
    void aQuantityWithABadUnit_isReported() {
        assertThat(MeasureValues.problems(MASS, value("amount", "1"))).containsExactly("unit must be a unit code");
        assertThat(MeasureValues.problems(MASS, value("amount", "1", "unit", "parsec"))).containsExactly("unit 'parsec' is unknown");
        assertThat(MeasureValues.problems(MASS, value("amount", "1", "unit", "m"))).containsExactly("unit 'm' is not allowed here");
        assertThat(MeasureValues.problems(GRAMS, value("amount", "1", "unit", "kg"))).containsExactly("unit 'kg' is not allowed here");
    }

    @Test
    void aQuantityKeepsToTheScale() {
        assertThat(MeasureValues.problems(GRAMS, value("amount", "1.25", "unit", "g"))).isEmpty();
        assertThat(MeasureValues.problems(GRAMS, value("amount", "1.250", "unit", "g"))).isEmpty();
        assertThat(MeasureValues.problems(GRAMS, value("amount", "1.255", "unit", "g"))).containsExactly("amount has more than 2 decimal places");
    }

    @Test
    void money_keepsToTheCurrencysMinorUnits() {
        assertThat(MeasureValues.problems(PRICE, value("amount", "19.99", "currency", "EUR"))).isEmpty();
        assertThat(MeasureValues.problems(PRICE, value("amount", "19.999", "currency", "EUR"))).containsExactly("amount has more than 2 decimal places");
        assertThat(MeasureValues.problems(PRICE, value("amount", "1500", "currency", "JPY"))).isEmpty();
        assertThat(MeasureValues.problems(PRICE, value("amount", "1500.5", "currency", "JPY"))).containsExactly("amount has more than 0 decimal places");
    }

    @Test
    void money_needsAnAllowedKnownCurrency() {
        assertThat(MeasureValues.problems(PRICE, value("amount", "1"))).containsExactly("currency must be an ISO 4217 code");
        assertThat(MeasureValues.problems(PRICE, value("amount", "1", "currency", "XXX"))).containsExactly("currency 'XXX' is unknown");
        assertThat(MeasureValues.problems(PRICE, value("amount", "1", "currency", "USD"))).containsExactly("currency 'USD' is not allowed here");
        assertThat(MeasureValues.problems(new MeasureView(Kind.MONEY, null, null, null, null, null), value("amount", "1", "currency", "USD"))).isEmpty();
    }

    @Test
    void dimensions_needAnAmountOnEveryAxisTheAttributeHas() {
        assertThat(MeasureValues.problems(FLAT, value("width", "120", "height", "80", "unit", "cm"))).isEmpty();
        assertThat(MeasureValues.problems(FLAT, value("width", "120", "unit", "cm"))).containsExactly("height must be a decimal number");
        assertThat(MeasureValues.problems(BOX, value("width", "1", "height", "2", "unit", "m"))).containsExactly("depth must be a decimal number");
        assertThat(MeasureValues.problems(BOX, value("width", "1", "height", "2", "depth", "3", "unit", "m"))).isEmpty();
    }

    @Test
    void dimensions_ofTwoAxes_haveNoDepth() {
        assertThat(MeasureValues.problems(FLAT, value("width", "1", "height", "2", "depth", "3", "unit", "m")))
                .containsExactly("dimensions has no depth: the attribute has two axes");
    }

    @Test
    void dimensions_areLengthsOfTheAllowedUnits() {
        assertThat(MeasureValues.problems(FLAT, value("width", "1", "height", "2", "unit", "kg"))).containsExactly("unit 'kg' is not allowed here");
        assertThat(MeasureValues.problems(FLAT, value("width", "1", "height", "2", "unit", "mm"))).containsExactly("unit 'mm' is not allowed here");
        assertThat(MeasureValues.problems(BOX, value("width", "1.5", "height", "2", "depth", "3", "unit", "m")))
                .as("one decimal place is the scale").isEmpty();
        assertThat(MeasureValues.problems(BOX, value("width", "1.55", "height", "2", "depth", "3", "unit", "m")))
                .containsExactly("width has more than 1 decimal places");
    }

    @Test
    void everyProblemOfAValueIsReported() {
        assertThat(MeasureValues.problems(FLAT, value("width", "x", "height", "y", "unit", "kg")))
                .containsExactly("width must be a decimal number", "height must be a decimal number", "unit 'kg' is not allowed here");
    }

    @Test
    void aQuantityIsStoredWithItsCanonicalAmountAndItsAmountInTheBaseUnit() {
        assertThat(MeasureValues.normalized(MASS, value("amount", "12.50", "unit", "g")))
                .containsExactly(Map.entry("amount", "12.5"), Map.entry("unit", "g"), Map.entry("baseAmount", 0.0125));
        assertThat(MeasureValues.normalized(ANY_UNIT, value("amount", "25", "unit", "degC")))
                .containsEntry("baseAmount", 298.15);
        assertThat(MeasureValues.normalized(MASS, value("amount", "0.00", "unit", "kg")))
                .containsEntry("amount", "0").containsEntry("baseAmount", 0.0);
    }

    @Test
    void whatAClientSendsUnderTheDerivedNames_isReplaced() {
        Map<String, Object> stored = MeasureValues.normalized(MASS, value("amount", "1", "unit", "kg", "baseAmount", 999, "extra", "x"));

        assertThat(stored).containsExactly(Map.entry("amount", "1"), Map.entry("unit", "kg"), Map.entry("baseAmount", 1.0));
    }

    @Test
    void moneyIsStoredWithItsAmountAsANumber() {
        assertThat(MeasureValues.normalized(PRICE, value("amount", "19.90", "currency", "EUR")))
                .containsExactly(Map.entry("amount", "19.9"), Map.entry("currency", "EUR"), Map.entry("baseAmount", 19.9));
    }

    @Test
    void anEmptyListOfAllowedUnitsOrCurrenciesRestrictsNothing() {
        MeasureView quantity = new MeasureView(Kind.QUANTITY, "MASS", List.of(), null, null, null);
        MeasureView money = new MeasureView(Kind.MONEY, null, null, null, List.of(), null);
        MeasureView dimensions = new MeasureView(Kind.DIMENSIONS, null, List.of(), null, null, null);

        assertThat(MeasureValues.problems(quantity, value("amount", "1", "unit", "t"))).isEmpty();
        assertThat(MeasureValues.problems(quantity, value("amount", "1", "unit", "m"))).containsExactly("unit 'm' is not allowed here");
        assertThat(MeasureValues.problems(money, value("amount", "1", "currency", "HUF"))).isEmpty();
        assertThat(MeasureValues.problems(dimensions, value("width", "1", "height", "2", "unit", "km"))).isEmpty();
    }

    @Test
    void aFloatAmountIsReadLikeADouble_andOnlyDimensionsOfThreeAxesAreThreeDimensional() {
        assertThat(MeasureValues.problems(MASS, value("amount", 1.5f, "unit", "kg"))).isEmpty();
        assertThat(MeasureValues.problems(MASS, value("amount", Float.POSITIVE_INFINITY, "unit", "kg"))).containsExactly("amount must be a decimal number");
        assertThat(MASS.threeDimensional()).isFalse();
        assertThat(new MeasureView(Kind.DIMENSIONS, null, null, null, null, null).threeDimensional()).isFalse();
        assertThat(new MeasureView(Kind.DIMENSIONS, null, null, null, null, 2).threeDimensional()).isFalse();
    }

    @Test
    void dimensionsAreStoredWithTheirAreaAndVolumeInMetres() {
        assertThat(MeasureValues.normalized(new MeasureView(Kind.DIMENSIONS, null, null, null, null, 3),
                value("width", "120", "height", "80", "depth", "50", "unit", "cm")))
                .containsExactly(Map.entry("width", "120"), Map.entry("height", "80"), Map.entry("depth", "50"), Map.entry("unit", "cm"),
                        Map.entry("baseArea", 0.96), Map.entry("baseVolume", 0.48));
        assertThat(MeasureValues.normalized(FLAT, value("width", "2", "height", "3", "unit", "m")))
                .containsExactly(Map.entry("width", "2"), Map.entry("height", "3"), Map.entry("unit", "m"), Map.entry("baseArea", 6.0));
    }
}
