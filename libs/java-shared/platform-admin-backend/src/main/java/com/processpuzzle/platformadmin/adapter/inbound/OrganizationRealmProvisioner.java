package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import com.processpuzzle.platformadmin.domain.event.OrganizationProvisionedEvent;
import com.processpuzzle.platformadmin.usecase.port.IdentityRealmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates a tenant's identity realm after the tenant's row is committed, and deletes it after the row
 * is gone.
 *
 * <p><b>Why after commit.</b> Creating a realm is an HTTP call to Keycloak, which has its own
 * durability and no part in this database's transaction. Inside the transaction it would hold a
 * connection open for a network round trip and — worse — a realm created for a transaction that then
 * rolled back would be an orphan nothing knows to clean up. After commit the failure mode is the
 * benign one: the row stays {@code PROVISIONING}, which is visible, diagnosable and retryable.
 *
 * <p><b>Why {@code REQUIRES_NEW}.</b> This is the part that fails silently if got wrong.
 * {@code AFTER_COMMIT} runs with the original transaction already completed, so a write on it is
 * discarded without an error — and the response to the request that triggered it still shows the
 * value the handler set in memory, so the loss only surfaces on the next read. A new transaction is
 * the only way the {@code ACTIVE} status actually lands.
 *
 * <p><b>Except this deployment's own stack organization.</b> A backend bootstraps an organization
 * whose key is its own stack key (see {@code ReservedOrganizationKeys}, which exempts that one key
 * from the reserved set so the bootstrap can claim it). That organization's realm is <em>not</em> a
 * tenant realm: it is created by infrastructure from
 * {@code tools/docker/keycloak/import/<stack>-realm.json}, with the stack's own public client and
 * users. Provisioning it here would add a tenant {@code processpuzzle-ui} client and the org roles to
 * it, and — far worse — deleting that organization row would delete the realm every user of the stack
 * authenticates against. So realm lifecycle is skipped for the stack's own key, and only for it.
 */
@Component
public class OrganizationRealmProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationRealmProvisioner.class);

    private final OrganizationRepository repository;
    private final IdentityRealmPort realms;
    private final String ownStackKey;

    /**
     * @param ownStackKey the organization key of the stack this deployment serves, whose realm is
     *                    infrastructure-owned. Same property, and same default chain, as
     *                    {@code ReservedOrganizationKeys} reads — the two answer different questions
     *                    about the one key, so they read it independently rather than through a
     *                    holder neither of them would otherwise need.
     */
    public OrganizationRealmProvisioner(
            OrganizationRepository repository,
            IdentityRealmPort realms,
            @Value("${platform-admin.stack-organization-key:${processpuzzle.security.stack-realm:}}")
            String ownStackKey) {
        this.repository = repository;
        this.realms = realms;
        this.ownStackKey = ownStackKey == null ? "" : ownStackKey.trim();
    }

    /**
     * Creates the realm and flips the tenant to {@code ACTIVE}.
     *
     * <p>A failure is logged and swallowed rather than rethrown. Nothing is listening that could act
     * on it — the originating request was answered 201 long before this ran — and letting it
     * propagate out of an after-commit listener produces an unhandled-exception entry with less
     * context than this one. The tenant staying {@code PROVISIONING} <em>is</em> the error report.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrganizationProvisioned(OrganizationProvisionedEvent event) {
        if (!realmIsReady(event)) {
            return;
        }

        Organization organization = repository.findById(event.orgKey()).orElse(null);
        if (organization == null) {
            LOG.warn("Organization '{}' disappeared before its realm was registered.", event.orgKey());
            return;
        }
        organization.setStatus(OrganizationStatus.ACTIVE);
        repository.save(organization);
        LOG.info("Organization '{}' is ACTIVE.", event.orgKey());
    }

    /**
     * Ensures the realm exists, and reports whether the tenant may be flipped to {@code ACTIVE}.
     *
     * <p>The stack's own realm counts as ready without a call: infrastructure created it, and it was
     * serving requests before this application started.
     */
    private boolean realmIsReady(OrganizationProvisionedEvent event) {
        if (isOwnStack(event.orgKey())) {
            LOG.info("Organization '{}' is this deployment's own stack; its realm is provided by "
                    + "infrastructure, so none is created here.", event.orgKey());
            return true;
        }
        try {
            realms.createRealm(event.orgKey(), event.organizationName(), event.defaultLocale());
            return true;
        } catch (RuntimeException ex) {
            LOG.error("Could not create the identity realm for organization '{}'. It stays PROVISIONING "
                    + "and the operation can be retried.", event.orgKey(), ex);
            return false;
        }
    }

    /**
     * Deletes the tenant's realm once the deletion has committed.
     *
     * <p>After commit, and not before — unlike the data cleanup other features do on this same event.
     * A {@code BEFORE_COMMIT} realm deletion could not be undone if the transaction then rolled back,
     * leaving a live tenant with no identity provider: strictly worse than the opposite failure, an
     * orphaned realm, which is inert and removable by hand.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        if (isOwnStack(event.orgKey())) {
            LOG.warn("Organization '{}' was deleted, but its realm is this deployment's own stack realm "
                    + "and belongs to infrastructure; it has been left in place.", event.orgKey());
            return;
        }
        try {
            realms.deleteRealm(event.orgKey());
        } catch (RuntimeException ex) {
            LOG.error("Organization '{}' was deleted but its identity realm could not be removed; "
                    + "delete it manually.", event.orgKey(), ex);
        }
    }

    private boolean isOwnStack(String orgKey) {
        return !ownStackKey.isBlank() && ownStackKey.equalsIgnoreCase(orgKey);
    }
}
