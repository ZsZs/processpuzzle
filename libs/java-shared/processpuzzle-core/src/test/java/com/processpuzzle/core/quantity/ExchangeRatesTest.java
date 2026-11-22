package com.processpuzzle.core.quantity;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What the shared parity cases do not say: the table's edges, and that the answer does not depend on entry order. */
class ExchangeRatesTest {

    private static final UnitCatalog CATALOG = UnitCatalog.standard();
    private static final LocalDate DAY = LocalDate.parse("2026-03-01");

    @Test
    void withoutRatesOnlyTheSameCurrencyConverts() {
        ExchangeRates none = ExchangeRates.none();

        assertThat(none.rate("EUR", "EUR", DAY)).isEqualByComparingTo("1");
        assertThat(ExchangeRates.of(List.of())).isSameAs(none);
        assertThatThrownBy(() -> none.rate("EUR", "USD", DAY)).isInstanceOfSatisfying(FundamentalTypeException.class,
            e -> assertThat(e.reason()).isEqualTo(FundamentalTypeException.Reason.NO_EXCHANGE_RATE));
    }

    @Test
    void theAnswerDoesNotDependOnTheOrderTheRatesWereEnteredIn() {
        List<ExchangeRate> rates = List.of(
            ExchangeRate.of("EUR", "USD", "1.1", "2026-01-01"), ExchangeRate.of("EUR", "USD", "1.2", "2026-02-01"),
            ExchangeRate.of("EUR", "GBP", "0.8", "2026-01-01"), ExchangeRate.of("GBP", "USD", "1.3", "2026-01-01"));
        ExchangeRates forward = ExchangeRates.of(rates);
        ExchangeRates backward = ExchangeRates.of(rates.reversed());

        assertThat(forward.rate("EUR", "USD", DAY)).isEqualByComparingTo("1.2");
        assertThat(backward.rate("EUR", "USD", DAY)).isEqualByComparingTo(forward.rate("EUR", "USD", DAY));
        assertThat(backward.rate("GBP", "EUR", DAY)).isEqualByComparingTo(forward.rate("GBP", "EUR", DAY));
    }

    @Test
    void aMoneyConvertsItself() {
        ExchangeRates rates = ExchangeRates.of(List.of(ExchangeRate.of("EUR", "USD", "1.1", "2026-01-01")));

        assertThat(Money.of("100", "EUR").convertTo("USD", rates, DAY, CATALOG)).isEqualTo(Money.of("110", "USD"));
    }

    @Test
    void aRateNeedsItsDate() {
        assertThatThrownBy(() -> ExchangeRate.of("EUR", "USD", "1.1", null)).isInstanceOfSatisfying(FundamentalTypeException.class,
            e -> assertThat(e.reason()).isEqualTo(FundamentalTypeException.Reason.INVALID_RATE));
    }
}
