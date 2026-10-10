package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its event catalog.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, so the delete joins the erasing transaction and a tenant is either
 * erased in every feature or in none. An {@code AFTER_COMMIT} listener's writes would be discarded
 * without an error.
 *
 * <p>One bulk statement; {@code event_definition} has no child or collection table, and nothing
 * outside the database holds the catalog, so there is nothing to do after commit.
 *
 * <p>Named, because other features declare a {@code TenantDataCleaner} too, and an application that
 * assembles several of them would otherwise have two beans of one name.
 */
@Component("eventTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final EventDefinitionRepository definitions;

    public TenantDataCleaner(EventDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        int erasedDefinitions = definitions.eraseByOrgKey(orgKey);

        LOG.info("Organization '{}' erased: {} event definition(s) deleted.", orgKey, erasedDefinitions);
    }
}
