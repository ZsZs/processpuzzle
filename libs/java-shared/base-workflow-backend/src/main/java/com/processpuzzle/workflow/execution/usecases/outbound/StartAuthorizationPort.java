package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Set;

/**
 * Checks whether the user requesting a workflow start holds one of the base-entity roles a
 * ROLE_DEFINITION start event authorizes. Asked about the <em>current</em> principal rather than
 * about a user id, unlike {@link RoleMembershipPort}: a start is always requested by the caller
 * themself, so the host application can answer from the caller's token.
 *
 * <p>Supplied by the host application, for the same reason {@link RoleMembershipPort} is; without
 * one, {@code StartEventAdmission} falls back to {@link PermitAllStartAuthorizationPort}.
 */
public interface StartAuthorizationPort {

    /**
     * @param entityRoleIds the {@code RoleDefinition.entityRoleId}s of the authorized roles; never
     *                      empty — a start event naming no role admits without asking
     */
    boolean currentPrincipalHoldsAny(String orgKey, Set<String> entityRoleIds);
}
