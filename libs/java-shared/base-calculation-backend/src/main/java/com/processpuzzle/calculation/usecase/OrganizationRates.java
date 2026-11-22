package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.engine.ExchangeRateScope;
import com.processpuzzle.core.quantity.ExchangeRateProvider;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Runs a calculation's evaluation with the exchange rates of the organization it is for. The rates come from whichever feature
 * implements {@link ExchangeRateProvider} (base-entity does); without one there are none, and a conversion between two
 * currencies is refused.
 */
@Component
class OrganizationRates {

    private final ExchangeRateProvider provider;

    OrganizationRates(ObjectProvider<ExchangeRateProvider> provider) {
        this.provider = provider.getIfAvailable(ExchangeRateProvider::noRates);
    }

    <T> T within(String orgKey, Supplier<T> evaluation) {
        return ExchangeRateScope.with(provider.forOrganization(orgKey), evaluation);
    }
}
