package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.i18n.StateTranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its state machines, their diagram layouts, and
 * its translations.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, in any order.</b> States and transitions are JSON columns of their state
 * machine's row, and a diagram refers to its state machine by {@code entityName} only, with no
 * foreign key — so the three tables are independent, and each goes in one statement.
 *
 * <p><b>Nothing after commit.</b> Nothing of this feature lives outside the database; the engine
 * reads the definitions on every transition rather than caching them.
 *
 * <p>Named, because base-app and base-rule declare a {@code TenantDataCleaner} too, and an
 * application that assembles several features would otherwise have two beans of one name.
 */
@Component("stateTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final StateMachineDefinitionRepository stateMachines;
    private final DiagramDefinitionRepository diagrams;
    private final StateTranslationRepository translations;

    public TenantDataCleaner(StateMachineDefinitionRepository stateMachines, DiagramDefinitionRepository diagrams,
                             StateTranslationRepository translations) {
        this.stateMachines = stateMachines;
        this.diagrams = diagrams;
        this.translations = translations;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();

        int erasedStateMachines = stateMachines.eraseByOrgKey(orgKey);
        int erasedDiagrams = diagrams.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);

        LOG.info("Organization '{}' erased: {} state machine(s), {} diagram(s) and {} translation bundle(s) deleted.",
                orgKey, erasedStateMachines, erasedDiagrams, erasedTranslations);
    }
}
