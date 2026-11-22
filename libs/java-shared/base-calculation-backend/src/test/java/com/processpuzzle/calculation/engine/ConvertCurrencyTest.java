package com.processpuzzle.calculation.engine;

import com.processpuzzle.core.quantity.ExchangeRate;
import com.processpuzzle.core.quantity.ExchangeRates;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code convert currency(amount, from, to[, date])} converts at the exchange rates of the organization the calculation runs for —
 * entered with {@link ExchangeRateScope} — rounded to the target's minor units, and refuses a pair without a rate.
 */
class ConvertCurrencyTest {

    private static final ExchangeRates ACME = ExchangeRates.of(List.of(
        ExchangeRate.of("EUR", "USD", "1.10", "2026-01-01"), ExchangeRate.of("EUR", "USD", "1.20", "2026-07-01"), ExchangeRate.of("EUR", "HUF", "400", "2026-01-01")));

    private static Object evaluate(String expression) {
        return ExchangeRateScope.with(ACME, () -> Calculations.FEEL.evaluate(expression, Map.of("net", new BigDecimal("100"))));
    }

    @Test
    void convertsAtTheRateValidOnTheDate() {
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\", date(\"2026-03-01\"))")).isEqualByComparingTo("110");
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\", date(\"2026-07-01\"))")).isEqualByComparingTo("120");
    }

    @Test
    void takesADateAndTimeOrIsoTextForTheDate() {
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\", date and time(\"2026-03-01T09:30:00\"))")).isEqualByComparingTo("110");
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\", date and time(\"2026-08-01T09:30:00Z\"))")).isEqualByComparingTo("120");
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\", \"2026-03-01\")")).isEqualByComparingTo("110");
    }

    @Test
    void withoutADateItIsTodaysRateAndDerivesTheInverseAndCrossRates() {
        assertThat((BigDecimal) evaluate("convert currency(net, \"EUR\", \"USD\")")).isEqualByComparingTo("120");
        assertThat((BigDecimal) evaluate("convert currency(120, \"USD\", \"EUR\")")).isEqualByComparingTo("100");
        assertThat((BigDecimal) evaluate("convert currency(120, \"USD\", \"HUF\")")).isEqualByComparingTo("40000");
    }

    @Test
    void roundsToTheTargetsMinorUnits() {
        assertThat((BigDecimal) evaluate("convert currency(1, \"USD\", \"EUR\", date(\"2026-03-01\"))")).isEqualByComparingTo("0.91");
        assertThat((BigDecimal) evaluate("convert currency(1.234, \"EUR\", \"HUF\", date(\"2026-03-01\"))")).isEqualByComparingTo("493.6");
    }

    @Test
    void aCalculationReadsTheRatesOfTheScopeItRunsIn() {
        String expression = "convert currency(net, \"EUR\", \"USD\", date(\"2026-03-01\"))";
        ExchangeRates other = ExchangeRates.of(List.of(ExchangeRate.of("EUR", "USD", "2", "2026-01-01")));

        assertThat((BigDecimal) ExchangeRateScope.with(other, () -> Calculations.FEEL.evaluate(expression, Map.of("net", new BigDecimal("100"))))).isEqualByComparingTo("200");
        assertThat((BigDecimal) evaluate(expression)).isEqualByComparingTo("110");
        assertThat(ExchangeRateScope.current()).isSameAs(ExchangeRates.none());
    }

    @Test
    void aPairWithoutARateAndABadArgumentAreRefused() {
        assertThatThrownBy(() -> evaluate("convert currency(net, \"EUR\", \"GBP\", date(\"2026-03-01\"))")).hasMessageContaining("no exchange rate");
        assertThatThrownBy(() -> evaluate("convert currency(net, \"EUR\", \"USD\", date(\"2025-03-01\"))")).hasMessageContaining("no exchange rate");
        assertThatThrownBy(() -> Calculations.FEEL.evaluate("convert currency(net, \"EUR\", \"USD\")", Map.of("net", new BigDecimal("1")))).hasMessageContaining("no exchange rate");
        assertThatThrownBy(() -> evaluate("convert currency(net, \"EUR\", \"USD\", \"yesterday\")")).hasMessageContaining("ISO date");
        assertThatThrownBy(() -> evaluate("convert currency(net, \"EUR\", \"USD\", 5)")).hasMessageContaining("expected a date");
        assertThatThrownBy(() -> evaluate("convert currency(net, \"EUR\", \"ZZZ\", date(\"2026-03-01\"))")).hasMessageContaining("ZZZ");
    }

    /** FEEL's own rule for a call whose arguments do not fit the function: the value is null, not an error. */
    @Test
    void anAmountThatIsNotANumberIsNotAMatchForTheFunction() {
        assertThat(evaluate("convert currency(\"a lot\", \"EUR\", \"USD\")")).isNull();
    }

    @Test
    void theFunctionExistsAsAFunctionNameOfTheEngine() {
        assertThat(Calculations.FEEL.checkExpression("convert currency(1, \"EUR\", \"USD\")")).isEmpty();
    }
}
