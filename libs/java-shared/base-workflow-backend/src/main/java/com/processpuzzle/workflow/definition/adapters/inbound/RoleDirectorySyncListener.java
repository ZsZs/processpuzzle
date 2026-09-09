package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionChangedEvent;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionDeletedEvent;
import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Projects each committed change to the role catalog into the identity provider's realm roles.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} is the deliberate opposite of base-app's
 * {@code TenantDataCleaner}, which uses {@code BEFORE_COMMIT}, and the two illustrate the trade the
 * phase buys. Before commit, the handler runs inside the writing transaction: its own database work
 * commits atomically with the write, and throwing rolls the write back. That is what a cascade of
 * <em>rows</em> needs. This handler writes to a different system altogether, over HTTP, which cannot
 * take part in the transaction — so before commit it would be able to publish a role into a realm and
 * then have the definition rolled out from under it, leaving a realm role no tenant ever authored and
 * nothing to ever remove it. After commit, the definition is a fact and the projection is
 * best-effort: a failure here is logged by {@link SyncRoleDirectory} and healed by
 * {@link RoleDirectoryReconciler} on the next start.
 *
 * <p>The corollary is that this listener cannot veto the change it observes, which is correct: the
 * catalog is the system of record, and an unreachable Keycloak is no reason to refuse a tenant the
 * role they authored.
 */
@Component
public class RoleDirectorySyncListener {

    private final SyncRoleDirectory syncRoleDirectory;

    public RoleDirectorySyncListener(SyncRoleDirectory syncRoleDirectory) {
        this.syncRoleDirectory = syncRoleDirectory;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoleDefinitionChanged(RoleDefinitionChangedEvent event) {
        syncRoleDirectory.sync(event.orgKey(), event.roleId(), event.name(), event.description());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoleDefinitionDeleted(RoleDefinitionDeletedEvent event) {
        syncRoleDirectory.remove(event.orgKey(), event.roleId());
    }
}
