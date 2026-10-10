package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.i18n.RuleTranslationRepository;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Deletes this feature's rows of an erased tenant: its rules, their field lists, and its
 * translations — and takes the tenant's rules out of the running engine once that has committed.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, child table first.</b> {@code deleteByOrgKey} loads every rule and removes
 * it one by one; a JPQL {@code delete} is one statement, but it does not cascade, so the
 * {@code rule_definition_fields} collection table goes first.
 *
 * <p><b>The engine only after commit.</b> It holds compiled rules in memory, keyed by tenant. Removing
 * them before the commit would leave a tenant whose erasure rolled back with rules the database still
 * has and the engine no longer evaluates. {@link RuleEngineSync#unregisterAfterCommit} defers it, and
 * the ids are read before the rows go.
 *
 * <p>Named, because base-app and base-starter declare a {@code TenantDataCleaner} too, and an
 * application that assembles several features would otherwise have two beans of one name.
 */
@Component("ruleTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final RuleDefinitionRepository rules;
    private final RuleTranslationRepository translations;
    private final RuleEngineSync engine;

    public TenantDataCleaner(RuleDefinitionRepository rules, RuleTranslationRepository translations,
                             RuleEngineSync engine) {
        this.rules = rules;
        this.translations = translations;
        this.engine = engine;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        List<String> ruleIds = rules.findIdsByOrgKey(orgKey);

        rules.eraseFieldsByOrgKey(orgKey);
        int erasedRules = rules.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);

        ruleIds.forEach(ruleId -> engine.unregisterAfterCommit(orgKey, ruleId));
        LOG.info("Organization '{}' erased: {} rule(s) and {} translation bundle(s) deleted.", orgKey,
                erasedRules, erasedTranslations);
    }
}
