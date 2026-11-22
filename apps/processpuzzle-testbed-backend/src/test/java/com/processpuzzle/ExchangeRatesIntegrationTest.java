package com.processpuzzle;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.usecases.inbound.ManageExchangeRates;
import com.processpuzzle.calculation.usecase.EvaluateCalculation;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An exchange rate an organization enters, through the real platform: a rule converts money at it with {@code to(...)}, a
 * calculation with {@code convert currency(...)}, a change or a deletion applies to the next conversion, and another
 * organization, which entered none, is refused.
 */
@SpringBootTest
class ExchangeRatesIntegrationTest {

    private static final String ORG = "exchange-rates";
    private static final String OTHER_ORG = "exchange-rates-other";
    private static final LocalDate DAY = LocalDate.parse("2026-03-01");
    private static final String RULE = "money(entity.total).to('EUR', '2026-03-01').eq(money('100 EUR'))";
    private static final Map<String, Object> ORDER = Map.of("total", Map.of("amount", "110", "currency", "USD"));

    private static final String CALCULATION = """
            calculation: net-in-usd
            name: Net in USD
            parameters:
              net: { type: number }
            decisions:
              inUsd:
                expr: "convert currency(net, \\"EUR\\", \\"USD\\", date(\\"2026-03-01\\"))"
            """;

    @Autowired private ManageExchangeRates rates;
    @Autowired private RuleEngine ruleEngine;
    @Autowired private EvaluateCalculation evaluate;

    private static OrganizationExchangeRate rate(String from, String to, String value, LocalDate validFrom) {
        OrganizationExchangeRate rate = new OrganizationExchangeRate();
        rate.setFromCurrency(from);
        rate.setToCurrency(to);
        rate.setRate(new BigDecimal(value));
        rate.setValidFrom(validFrom);
        return rate;
    }

    private boolean ruleHolds(String org) {
        return ruleEngine.evaluate(RuleKey.of(org, "limit"), RULE, ORDER);
    }

    private EvaluateCalculation.Evaluation calculated(String org) {
        return evaluate.evaluate(org, "net-in-usd", Channel.DRAFT, Map.of("net", 100), CALCULATION);
    }

    @Test
    void aRateIsReadByTheRulesAndTheCalculationsOfItsOrganizationOnly() {
        assertThatThrownBy(() -> ruleHolds(ORG)).hasMessageContaining("no exchange rate");

        rates.create(ORG, rate("EUR", "USD", "1.1", DAY));

        assertThat(ruleHolds(ORG)).as("110 USD at 1.1 is 100 EUR").isTrue();
        assertThat(calculated(ORG).error()).isNull();
        assertThat(new BigDecimal(String.valueOf(calculated(ORG).result().values().get("inUsd")))).isEqualByComparingTo("110");
        assertThatThrownBy(() -> ruleHolds(OTHER_ORG)).hasMessageContaining("no exchange rate");
        assertThat(calculated(OTHER_ORG).error()).as("a calculation without a rate fails, it does not answer null").contains("no exchange rate");
    }

    @Test
    void aChangedOrDeletedRateAppliesToTheNextConversion() {
        String org = ORG + "-changes";
        OrganizationExchangeRate created = rates.create(org, rate("EUR", "USD", "1.1", DAY));
        assertThat(ruleHolds(org)).isTrue();

        rates.replace(org, created.getId(), rate("EUR", "USD", "1.2", DAY));
        assertThat(ruleHolds(org)).as("110 USD at 1.2 is no longer 100 EUR").isFalse();

        rates.delete(org, created.getId());
        assertThatThrownBy(() -> ruleHolds(org)).hasMessageContaining("no exchange rate");
        assertThat(rates.list(org, null, null, PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    void aPairHasOneRatePerDateAndABadRateIsRefused() {
        String org = ORG + "-validation";
        rates.create(org, rate("EUR", "USD", "1.1", DAY));

        assertThatThrownBy(() -> rates.create(org, rate("EUR", "USD", "1.3", DAY))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> rates.create(org, rate("EUR", "EUR", "1", DAY))).isInstanceOf(ValidationException.class);
        assertThat(rates.create(org, rate("EUR", "USD", "1.3", DAY.plusDays(30))).getId()).isNotNull();
    }
}
