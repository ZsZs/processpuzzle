package com.processpuzzle.baseentity.exchange.adapters.inbound;

import com.processpuzzle.baseentity.exchange.OrganizationExchangeRates;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the exchange rates of an erased tenant. {@code BEFORE_COMMIT}, so the delete joins the erasing transaction (an
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error). Named, because every feature declares a cleaner
 * and an application that assembles several would otherwise have two beans of one name.
 */
@Component("exchangeRateTenantDataCleaner")
class ExchangeRateTenantDataCleaner {

    private final OrganizationExchangeRateRepository rates;
    private final OrganizationExchangeRates tables;

    ExchangeRateTenantDataCleaner(OrganizationExchangeRateRepository rates, OrganizationExchangeRates tables) {
        this.rates = rates;
        this.tables = tables;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onOrganizationDeleted(OrganizationDeletedEvent event) {
        rates.eraseByOrgKey(event.orgKey());
        tables.evict(event.orgKey());
    }
}
