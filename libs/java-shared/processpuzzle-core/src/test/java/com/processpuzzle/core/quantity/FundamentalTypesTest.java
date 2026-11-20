package com.processpuzzle.core.quantity;

import com.processpuzzle.core.quantity.FundamentalTypeException.Reason;
import com.processpuzzle.core.quantity.UnitCatalog.CurrencyDefinition;
import com.processpuzzle.core.quantity.UnitCatalog.Data;
import com.processpuzzle.core.quantity.UnitCatalog.KindData;
import com.processpuzzle.core.quantity.UnitCatalog.UnitData;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundamentalTypesTest {

    private static final UnitCatalog CATALOG = UnitCatalog.standard();

    private static void refused(ThrowingCallable action, Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(FundamentalTypeException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    private static Quantity q(String amount, String unit) {
        return Quantity.of(amount, unit);
    }

    @Nested
    class Decimal {

        @Test
        void readsDecimalTextAndRefusesAnythingElse() {
            assertThat(Decimals.text(Decimals.parse(" 12.50 "))).isEqualTo("12.5");
            refused(() -> Decimals.parse("1e5"), Reason.INVALID_AMOUNT);
            refused(() -> Decimals.parse("12,5"), Reason.INVALID_AMOUNT);
            refused(() -> Decimals.parse(null), Reason.INVALID_AMOUNT);
        }

        @Test
        void writesPlainNotation() {
            assertThat(Decimals.text(new BigDecimal("1E+3"))).isEqualTo("1000");
            assertThat(Decimals.text(new BigDecimal("-0.00"))).isEqualTo("0");
        }

        @Test
        void refusesDivisionByZero() {
            refused(() -> Decimals.divide(BigDecimal.ONE, BigDecimal.ZERO), Reason.DIVISION_BY_ZERO);
        }
    }

    @Nested
    class Catalog {

        @Test
        void knowsEachUnitByItsCodeAndTheKindItMeasures() {
            assertThat(CATALOG.unit("degC")).satisfies(unit -> {
                assertThat(unit.symbol()).isEqualTo("°C");
                assertThat(unit.kind()).isEqualTo("TEMPERATURE");
                assertThat(unit.hasOffset()).isTrue();
            });
            assertThat(CATALOG.findUnit("parsec")).isEmpty();
            assertThat(CATALOG.unitsOf("TIME")).extracting(UnitCatalog.UnitDefinition::code).containsExactly("ms", "s", "min", "h", "d", "wk");
            assertThat(CATALOG.kinds()).extracting(UnitCatalog.QuantityKindDefinition::code).contains("LENGTH", "COUNT");
        }

        @Test
        void knowsIsoCurrenciesWithTheirMinorUnits() {
            assertThat(CATALOG.currency("JPY").minorUnits()).isZero();
            assertThat(CATALOG.findCurrency("XXX")).isEmpty();
            assertThat(CATALOG.currencies()).hasSizeGreaterThan(140);
        }

        @Test
        void refusesUnknownCodes() {
            refused(() -> CATALOG.unit("parsec"), Reason.UNKNOWN_UNIT);
            refused(() -> CATALOG.kind("LUMINOSITY"), Reason.UNKNOWN_KIND);
            refused(() -> CATALOG.currency("XXX"), Reason.UNKNOWN_CURRENCY);
        }

        @Test
        void refusesDuplicatesAndKindsWithoutAProperBaseUnit() {
            UnitData metre = new UnitData("m", "m", "metre", "1", null, null);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A", metre), kind("A")), List.of())), Reason.INVALID_CATALOG);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A", metre, metre)), List.of())), Reason.INVALID_CATALOG);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A", new UnitData("m", "m", "metre", "2", null, null))), List.of())), Reason.INVALID_CATALOG);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A", new UnitData("m", "m", "metre", "1", "2", null))), List.of())), Reason.INVALID_CATALOG);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A", new UnitData("m", "m", "metre", "1", null, "1"))), List.of())), Reason.INVALID_CATALOG);
            refused(() -> new UnitCatalog(new Data(List.of(kind("A")), List.of())), Reason.INVALID_CATALOG);
            CurrencyDefinition eur = new CurrencyDefinition("EUR", 2);
            refused(() -> new UnitCatalog(new Data(List.of(), List.of(eur, eur))), Reason.INVALID_CATALOG);
        }

        @Test
        void refusesAMissingOrUnreadableResource() {
            refused(() -> UnitCatalog.load(null), Reason.INVALID_CATALOG);
            refused(() -> UnitCatalog.load(new ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8))), Reason.INVALID_CATALOG);
        }

        private KindData kind(String code, UnitData... units) {
            return new KindData(code, code, "m", List.of(units));
        }
    }

    @Nested
    class Quantities {

        @Test
        void keepsItsAmountNormalized() {
            assertThat(q("1.50", "kg")).isEqualTo(q("1.5", "kg"));
            assertThat(q("1.50", "kg").amountText()).isEqualTo("1.5");
            refused(() -> new Quantity(null, "kg"), Reason.INVALID_AMOUNT);
            refused(() -> new Quantity(BigDecimal.ONE, null), Reason.INVALID_AMOUNT);
        }

        @Test
        void comparesAcrossUnitsOfOneKind() {
            assertThat(q("1", "kg").compareTo(q("999", "g"), CATALOG)).isPositive();
            assertThat(q("1000", "g").compareTo(q("1", "kg"), CATALOG)).isZero();
            assertThat(q("0", "degC").compareTo(q("33", "degF"), CATALOG)).isNegative();
        }

        @Test
        void refusesToRelateDifferentKinds() {
            refused(() -> q("1", "kg").compareTo(q("1", "m"), CATALOG), Reason.KIND_MISMATCH);
            refused(() -> q("1", "kg").convertTo("l", CATALOG), Reason.KIND_MISMATCH);
        }

        @Test
        void addsAndSubtractsInTheFirstOperandsUnit() {
            assertThat(q("1", "kg").plus(q("250", "g"), CATALOG)).isEqualTo(q("1.25", "kg"));
            assertThat(q("1", "h").minus(q("15", "min"), CATALOG)).isEqualTo(q("0.75", "h"));
        }

        @Test
        void scalesByPlainNumbers() {
            assertThat(q("2.5", "m").times(new BigDecimal("3"), CATALOG)).isEqualTo(q("7.5", "m"));
            assertThat(q("1", "m").dividedBy(new BigDecimal("3"), CATALOG)).isEqualTo(q("0.33333333333333333333", "m"));
            refused(() -> q("1", "m").dividedBy(BigDecimal.ZERO, CATALOG), Reason.DIVISION_BY_ZERO);
        }

        @Test
        void refusesArithmeticOnOffsetScales() {
            Quantity celsius = q("20", "degC");
            refused(() -> celsius.plus(celsius, CATALOG), Reason.OFFSET_ARITHMETIC);
            refused(() -> q("1", "K").plus(celsius, CATALOG), Reason.OFFSET_ARITHMETIC);
            refused(() -> celsius.times(BigDecimal.TWO, CATALOG), Reason.OFFSET_ARITHMETIC);
            refused(() -> celsius.dividedBy(BigDecimal.TWO, CATALOG), Reason.OFFSET_ARITHMETIC);
        }

        @Test
        void roundsHalfUpToAScale() {
            assertThat(q("1.2345", "kg").rounded(3)).isEqualTo(q("1.235", "kg"));
        }

        @Test
        void parsesAmountAndUnit() {
            assertThat(Quantity.parse(" -1.50  km/h ", CATALOG)).isEqualTo(q("-1.5", "km/h"));
            refused(() -> Quantity.parse("5kg", CATALOG), Reason.INVALID_AMOUNT);
            refused(() -> Quantity.parse(null, CATALOG), Reason.INVALID_AMOUNT);
            refused(() -> Quantity.parse("5 parsec", CATALOG), Reason.UNKNOWN_UNIT);
        }
    }

    @Nested
    class Monies {

        private Money eur(String amount) {
            return Money.of(amount, "EUR");
        }

        @Test
        void combinesAndComparesOneCurrency() {
            assertThat(eur("10.10").plus(eur("0.90"))).isEqualTo(eur("11"));
            assertThat(eur("10").minus(eur("0.01"))).isEqualTo(eur("9.99"));
            assertThat(eur("1").compareTo(eur("1.00"))).isZero();
        }

        @Test
        void neverMixesCurrencies() {
            refused(() -> eur("1").plus(Money.of("1", "USD")), Reason.CURRENCY_MISMATCH);
            refused(() -> eur("1").compareTo(Money.of("1", "USD")), Reason.CURRENCY_MISMATCH);
            refused(() -> new Money(null, "EUR"), Reason.INVALID_AMOUNT);
            refused(() -> new Money(BigDecimal.ONE, null), Reason.INVALID_AMOUNT);
        }

        @Test
        void scalesUnroundedUntilRoundedExplicitly() {
            assertThat(eur("19.99").times(new BigDecimal("0.27"))).isEqualTo(eur("5.3973"));
            assertThat(eur("100").dividedBy(new BigDecimal("3")).rounded(CATALOG).amountText()).isEqualTo("33.33");
            refused(() -> eur("1").dividedBy(BigDecimal.ZERO), Reason.DIVISION_BY_ZERO);
        }

        @Test
        void parsesAmountAndCurrency() {
            assertThat(Money.parse("100 EUR", CATALOG)).isEqualTo(eur("100"));
            refused(() -> Money.parse("100 XXX", CATALOG), Reason.UNKNOWN_CURRENCY);
        }
    }

    @Nested
    class DimensionValues {

        @Test
        void convertsEveryAxis() {
            Dimensions box = new Dimensions(new BigDecimal("1.2"), new BigDecimal("0.8"), new BigDecimal("0.5"), "m");
            assertThat(box.convertTo("cm", CATALOG)).isEqualTo(new Dimensions(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("50"), "cm"));
            Dimensions sheet = new Dimensions(BigDecimal.ONE, BigDecimal.TWO, null, "m");
            assertThat(sheet.convertTo("mm", CATALOG)).isEqualTo(new Dimensions(new BigDecimal("1000"), new BigDecimal("2000"), null, "mm"));
            assertThat(sheet.isThreeDimensional()).isFalse();
        }

        @Test
        void areLengthsAndOnly3dOnesHaveAVolume() {
            refused(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ONE, null, "kg").area(CATALOG), Reason.KIND_MISMATCH);
            refused(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ONE, null, "m").volume(CATALOG), Reason.KIND_MISMATCH);
            refused(() -> new Dimensions(null, BigDecimal.ONE, null, "m"), Reason.INVALID_AMOUNT);
            refused(() -> new Dimensions(BigDecimal.ONE, null, null, "m"), Reason.INVALID_AMOUNT);
            refused(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ONE, null, null), Reason.INVALID_AMOUNT);
        }
    }

    @Nested
    class Durations {

        @Test
        void onlyWriteTimeQuantitiesThatAreNotNegative() {
            refused(() -> IsoDurations.toIsoDuration(q("1", "m"), CATALOG), Reason.KIND_MISMATCH);
            refused(() -> IsoDurations.toIsoDuration(q("-1", "s"), CATALOG), Reason.INVALID_DURATION);
            refused(() -> IsoDurations.toQuantity(null, "s", CATALOG), Reason.INVALID_DURATION);
        }

        @Test
        void neverOverflowAUnit() {
            assertThat(IsoDurations.toIsoDuration(q("86399.999999999999999999", "s"), CATALOG)).isEqualTo("PT23H59M59.999999999999999999S");
        }
    }
}
