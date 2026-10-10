package com.processpuzzle.shared.event;

/**
 * A tenant is being erased, and every feature holding data scoped by {@code orgKey} must remove its
 * own rows.
 *
 * <p><b>Who publishes it.</b> Each application that holds tenant data publishes it for its own
 * database, inside one transaction: {@code platform-admin}'s erasure in the tenant registry, as the
 * last step of a tenant erasure, and {@code processpuzzle-custom-backend}'s {@code EraseOrganization}
 * for the customer application's data, which is the step before it. The event never crosses a service
 * boundary; each side announces it to the features assembled next to it.
 *
 * <p><b>Rows: {@code BEFORE_COMMIT}, child tables first.</b> Listeners use
 * {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}: their deletes then join the publishing
 * transaction and commit with it, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener that writes has its work silently discarded — the transaction it
 * runs in is already finished — which is exactly the bug this event exists to avoid. Prefer one bulk
 * {@code delete … where orgKey = :orgKey} per table over a derived {@code deleteByOrgKey}, which
 * loads every row first; a bulk delete does not cascade, so collection and child tables go first.
 *
 * <p><b>Everything else: after commit, idempotent.</b> Files in object storage, identity-provider
 * objects, in-memory registries and calls to other services cannot be rolled back with the
 * transaction, so a listener collects what it needs <em>before</em> its rows go and acts only once
 * the transaction has committed — through {@code TransactionSynchronization}, an
 * {@code AFTER_COMMIT} listener on an event of its own, or {@link StoredObjectsReleasedEvent} for
 * files. A rolled-back erasure then leaves all of it untouched. These steps must be idempotent and
 * must not throw: the erasure may be retried, and nobody is left to catch an exception after commit.
 *
 * <p><b>The subscribers.</b> Every feature that stores tenant data, each deleting only its own:
 * <ul>
 *   <li>base-app — app and module definitions, translations;</li>
 *   <li>base-starter — installed starters and definition provenance;</li>
 *   <li>base-entity — definitions and their attributes, objects, translations; releases the files
 *       the objects' ARTIFACT values referred to;</li>
 *   <li>base-event — the event catalog;</li>
 *   <li>base-rule — rules and their field lists, translations; unregisters the rules from the
 *       engine after commit;</li>
 *   <li>base-state — state machines, diagrams, translations;</li>
 *   <li>base-widget — widget definitions, translations;</li>
 *   <li>base-document — documents, drafts, published versions, translations;</li>
 *   <li>base-workflow — instances and their tasks, artifacts, events, start-timer schedules and
 *       instance counter, then definitions and translations; deletes the tenant's role groups from
 *       the identity provider after commit;</li>
 *   <li>base-ai — recognitions and their collection tables, enrollment photos, media uploads, vision
 *       job tickets, recognition profiles; deletes the tenant's media and discards its open vision
 *       jobs after commit.</li>
 * </ul>
 * A feature that adds a table subscribes here, and {@code processpuzzle-custom-backend}'s
 * erasure-completeness test fails until it does.
 *
 * @param orgKey the tenant being erased
 */
public record OrganizationDeletedEvent(String orgKey) {
}
