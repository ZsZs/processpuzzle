package com.processpuzzle.workflow.definition.domain.event;

/**
 * Published when a {@code RoleDefinition} has been removed from an organization's catalog.
 *
 * <p><b>Observe with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}</b>, for the reason
 * given on {@link RoleDefinitionChangedEvent} and one more that is specific to deletion: before
 * commit, the delete can still be refused — {@code DeleteRoleDefinitionUseCase} rejects a role a
 * workflow still names — and a projection that had already dropped the realm role would have
 * revoked it from every user who held it, for a role that in the end never went away.
 *
 * @param orgKey the organization whose catalog changed; also the realm name, by the platform's
 *               naming convention
 * @param roleId the code of the role that was removed
 */
public record RoleDefinitionDeletedEvent(String orgKey, String roleId) {
}
