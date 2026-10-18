package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its calculations and their runs.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, so the delete joins the erasing transaction and a tenant is either erased in
 * every feature or in none. Named, because other features declare a {@code TenantDataCleaner} too.
 */
@Component("calculationTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final CalculationDefinitionRepository definitions;
    private final CalculationRunRepository runs;

    public TenantDataCleaner(CalculationDefinitionRepository definitions, CalculationRunRepository runs) {
        this.definitions = definitions;
        this.runs = runs;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        int erasedRuns = runs.eraseByOrgKey(orgKey);
        int erasedDefinitions = definitions.eraseByOrgKey(orgKey);
        LOG.info("Organization '{}' erased: {} calculation(s) and {} run(s) deleted.", orgKey, erasedDefinitions, erasedRuns);
    }
}
