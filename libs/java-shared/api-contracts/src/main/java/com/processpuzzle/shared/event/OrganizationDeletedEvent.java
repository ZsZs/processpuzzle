package com.processpuzzle.shared.event;

/**
 * A tenant is being deleted, and every feature holding data scoped by {@code orgKey} should remove
 * its own rows.
 *
 * <p>Published by {@code DeleteOrganization} <em>inside</em> the deleting transaction. Listeners must
 * therefore use {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}: their writes then join
 * the same transaction and commit with the deletion, so a tenant cannot end up half-deleted. An
 * {@code AFTER_COMMIT} listener that writes has its work silently discarded — the transaction it
 * runs in is already finished — which is exactly the bug this event exists to avoid, so it is worth
 * stating rather than leaving to be rediscovered.
 *
 * <p>This event is why {@code platform-admin} can own the tenant without depending on the features
 * that live inside one. Today only {@code base-app} subscribes, deleting its app and module
 * definitions. Entity, rule, state and workflow data is organization-scoped by contract too and is
 * still not cleaned up; each of those features subscribes here when it grows an organization-aware
 * backend, and none of them needs a change on this side to do it.
 *
 * @param orgKey the tenant being deleted
 */
public record OrganizationDeletedEvent(String orgKey) {
}
