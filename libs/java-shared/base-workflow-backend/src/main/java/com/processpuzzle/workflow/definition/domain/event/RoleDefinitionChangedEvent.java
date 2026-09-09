package com.processpuzzle.workflow.definition.domain.event;

/**
 * Published when a {@code RoleDefinition} has been created or replaced in an organization's catalog.
 *
 * <p><b>Observe with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.</b> The required
 * phase is part of the contract, not an implementation detail of the one listener that exists today.
 * The catalog row is the system of record and the identity provider is a projection of it, so a
 * projection built from a change that then rolled back would be worse than no projection at all —
 * the realm would carry a role no tenant ever authored, and nothing would ever remove it. After
 * commit, the write is a fact and the projection can be retried until it succeeds (see
 * {@code RoleDirectoryReconciler}).
 *
 * <p>Carries the projected fields rather than the aggregate, so an observer never has to read the
 * definition back — by the time an {@code AFTER_COMMIT} listener runs, a concurrent request may
 * already have replaced it, and the projection would then describe a state this event never meant.
 *
 * @param orgKey      the organization whose catalog changed; also the realm name, by the platform's
 *                    naming convention
 * @param roleId      the role's code, unique per organization
 * @param name        the role's display name
 * @param description the role's description, or {@code null} when the author gave none
 */
public record RoleDefinitionChangedEvent(String orgKey, String roleId, String name, String description) {
}
