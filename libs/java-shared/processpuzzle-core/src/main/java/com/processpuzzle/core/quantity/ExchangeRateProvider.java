package com.processpuzzle.core.quantity;

/**
 * Where a feature reads the exchange rates of an organization from — the port {@code base-entity} implements, the way it
 * implements {@link UnitCatalogProvider}. A feature that converts money (a rule, a calculation) depends on this port and
 * not on the feature that stores the rates; without an implementation, {@link #noRates()} is what it reads, and every
 * conversion between two currencies is refused.
 */
@FunctionalInterface
public interface ExchangeRateProvider {

    /** The organization's rates; never null. A null organization has none. */
    ExchangeRates forOrganization(String orgKey);

    /** The default: no rates for anyone. */
    static ExchangeRateProvider noRates() {
        return orgKey -> ExchangeRates.none();
    }
}
