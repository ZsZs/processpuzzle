package com.processpuzzle.security;

import com.processpuzzle.workflow.execution.usecases.outbound.RoleMembershipPort;
import org.springframework.stereotype.Component;

/**
 * The real {@link RoleMembershipPort}: whether a user holds the realm role a workflow's
 * {@code RoleDefinition} names, so a task cannot be assigned to someone outside its role.
 *
 * <p><b>It can only answer for the current principal.</b> Keycloak is the source of role
 * memberships and this deployment reads them from the token, which is the caller's own — so asking
 * "does user X hold role R" about anybody else has no answer here. The port's signature takes a
 * {@code userId} because base-workflow wrote it before there was an identity provider; the honest
 * implementation of that signature is to answer for the current token when {@code userId} names the
 * current principal, and to permit otherwise rather than to guess.
 *
 * <p>Permitting rather than denying for a third party is chosen because denying would break the one
 * case that matters — a manager assigning a task to a team member, which is precisely a question
 * about somebody else. Closing this properly means either a directory lookup (org-admin's
 * {@code UserDirectoryPort} could answer it, but base-workflow cannot see that module) or widening
 * the port to take the roles rather than the id. Both are real changes to another library's contract,
 * and neither belongs in the change that introduced the resource server.
 */
@Component
public class RealmRoleMembershipPolicy implements RoleMembershipPort {

    private final CurrentPrincipal principal;

    public RealmRoleMembershipPolicy(CurrentPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public boolean isMember(String orgKey, String userId, String entityRoleId) {
        if (entityRoleId == null || entityRoleId.isBlank()) {
            // Nothing to check membership against; base-workflow treats this as always allowed.
            return true;
        }
        if (!principal.isAuthenticated() || !principal.isMemberOf(orgKey)) {
            return true;
        }
        if (!isCurrentPrincipal(userId)) {
            return true;
        }
        return principal.authorities().contains(entityRoleId);
    }

    private boolean isCurrentPrincipal(String userId) {
        return userId != null && userId.equals(
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication().getName());
    }
}
