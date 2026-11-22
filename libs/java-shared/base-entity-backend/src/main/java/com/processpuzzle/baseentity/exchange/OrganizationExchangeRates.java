package com.processpuzzle.baseentity.exchange;

import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRate;
import com.processpuzzle.baseentity.exchange.domain.OrganizationExchangeRateRepository;
import com.processpuzzle.core.quantity.ExchangeRate;
import com.processpuzzle.core.quantity.ExchangeRateProvider;
import com.processpuzzle.core.quantity.ExchangeRates;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The exchange rates of each organization, built once and kept — a rule or a calculation reads them on every conversion —
 * and dropped after the change that invalidates them has committed, so a read in between cannot rebuild the old table and keep
 * it. The cache lives in this process, which is how the platform runs (one backend per stack), as the unit catalogs' does.
 */
@Component
public class OrganizationExchangeRates implements ExchangeRateProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationExchangeRates.class);

    private final OrganizationExchangeRateRepository rates;
    private final Map<String, ExchangeRates> tables = new ConcurrentHashMap<>();

    public OrganizationExchangeRates(OrganizationExchangeRateRepository rates) {
        this.rates = rates;
    }

    @Override
    public ExchangeRates forOrganization(String orgKey) {
        if (orgKey == null) {
            return ExchangeRates.none();
        }
        return tables.computeIfAbsent(orgKey, this::build);
    }

    /** Forgets the organization's rates once the transaction in progress commits (at once when there is none). */
    public void evict(String orgKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tables.remove(orgKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                tables.remove(orgKey);
            }
        });
    }

    private ExchangeRates build(String orgKey) {
        List<ExchangeRate> entries = new ArrayList<>();
        for (OrganizationExchangeRate row : rates.findAllByOrgKey(orgKey)) {
            try {
                entries.add(new ExchangeRate(row.getFromCurrency(), row.getToCurrency(), row.getRate(), row.getValidFrom()));
            } catch (FundamentalTypeException e) {
                // Saving refuses what the table would, so this is a row written some other way. Leaving it out keeps the others
                // working; a conversion that needed it is then refused for want of a rate, loudly.
                LOG.warn("Exchange rate {} to {} of organization '{}' is not usable: {}", row.getFromCurrency(), row.getToCurrency(), orgKey, e.getMessage());
            }
        }
        return ExchangeRates.of(entries);
    }
}
