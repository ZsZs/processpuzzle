package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.i18n.CalculationTranslationRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its calculations, their runs and its translation bundles.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, so the delete joins the erasing transaction and a tenant is either erased in
 * every feature or in none. Named, because other features declare a {@code TenantDataCleaner} too.
 */
@Component("calculationTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final CalculationDefinitionRepository definitions;
    private final CalculationRunRepository runs;
    private final CalculationTranslationRepository translations;

    public TenantDataCleaner(CalculationDefinitionRepository definitions, CalculationRunRepository runs,
                             CalculationTranslationRepository translations) {
        this.definitions = definitions;
        this.runs = runs;
        this.translations = translations;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        int erasedRuns = runs.eraseByOrgKey(orgKey);
        int erasedDefinitions = definitions.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);
        LOG.info("Organization '{}' erased: {} calculation(s), {} run(s) and {} translation bundle(s) deleted.", orgKey,
                erasedDefinitions, erasedRuns, erasedTranslations);
    }
}
