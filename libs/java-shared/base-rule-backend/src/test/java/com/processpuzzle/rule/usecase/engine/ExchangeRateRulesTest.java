package com.processpuzzle.rule.usecase.engine;

import com.processpuzzle.core.quantity.ExchangeRate;
import com.processpuzzle.core.quantity.ExchangeRateProvider;
import com.processpuzzle.core.quantity.ExchangeRates;
import com.processpuzzle.core.quantity.UnitCatalogProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A rule converts money at the exchange rates of the organization it belongs to: one with a EUR to USD rate can compare an
 * order in USD against a limit in EUR, another cannot — on the same engine and thread, whose compiled rule is reused.
 */
class ExchangeRateRulesTest {

    private static final Map<String, Object> ORDER = Map.of("total", Map.of("amount", "110", "currency", "USD"));

    private RuleEngine engine;

    @BeforeEach
    void setUp() {
        ExchangeRates acme = ExchangeRates.of(List.of(
            ExchangeRate.of("EUR", "USD", "1.10", "2026-01-01"), ExchangeRate.of("EUR", "USD", "1.20", "2026-07-01")));
        ExchangeRateProvider rates = orgKey -> "acme".equals(orgKey) ? acme : ExchangeRates.none();
        engine = new RuleEngine(UnitCatalogProvider.standardOnly(), rates);
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private boolean holds(String org, String expression) {
        return engine.evaluate(RuleKey.of(org, "limit"), expression, ORDER);
    }

    @Test
    void aRuleConvertsAtTheRateValidOnTheGivenDate() {
        assertThat(holds("acme", "money(entity.total).to('EUR', '2026-03-01').eq(money('100 EUR'))")).isTrue();
        assertThat(holds("acme", "money(entity.total).to('EUR', '2026-08-01').eq(money('91.67 EUR'))")).isTrue();
    }

    @Test
    void aDateTimeTextAndAJavaScriptDateAreDatesToo() {
        assertThat(holds("acme", "money(entity.total).to('EUR', '2026-03-01T09:30:00Z').eq(money('100 EUR'))")).isTrue();
        assertThat(holds("acme", "money(entity.total).to('EUR', new Date(2026, 2, 1)).eq(money('100 EUR'))")).isTrue();
    }

    @Test
    void withoutADateItIsTodaysRate() {
        assertThat(holds("acme", "money('120 USD').to('EUR').eq(money('100 EUR'))")).isTrue();
    }

    @Test
    void theSameCurrencyConvertsToItselfWithoutRates() {
        assertThat(holds("other", "money('5 EUR').to('EUR').eq(money('5 EUR'))")).isTrue();
    }

    @Test
    void anotherOrganizationHasNoRatesAndIsRefused() {
        assertThat(holds("acme", "money(entity.total).to('EUR', '2026-03-01').gt(money('1 EUR'))")).isTrue();
        assertThatThrownBy(() -> holds("other", "money(entity.total).to('EUR', '2026-03-01').gt(money('1 EUR'))")).hasMessageContaining("no exchange rate");
        assertThat(holds("acme", "money(entity.total).to('EUR', '2026-03-01').gt(money('1 EUR'))")).as("and back again").isTrue();
    }

    @Test
    void aDateBeforeTheFirstRateAndABadDateAreRefused() {
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to('EUR', '2025-01-01').gt(money('1 EUR'))")).hasMessageContaining("no exchange rate");
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to('EUR', 'yesterday').gt(money('1 EUR'))")).hasMessageContaining("ISO date");
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to('EUR', 42).gt(money('1 EUR'))")).hasMessageContaining("ISO date");
    }

    @Test
    void theCallTakesACurrencyCodeAndAtMostADate() {
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to().gt(money('1 EUR'))")).hasMessageContaining("takes a currency code");
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to('EUR', '2026-03-01', 1).gt(money('1 EUR'))")).hasMessageContaining("takes a currency code");
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to(5).gt(money('1 EUR'))")).hasMessageContaining("currency code");
        assertThatThrownBy(() -> holds("acme", "money(entity.total).to('ZZZ', '2026-03-01').gt(money('1 EUR'))")).hasMessageContaining("ZZZ");
    }
}
