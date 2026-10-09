package com.processpuzzle.security;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.workflow.execution.usecases.outbound.StartAuthorizationPort;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The real {@link StartAuthorizationPort}: whether the caller's token carries one of the realm roles
 * a ROLE_DEFINITION start event authorizes.
 *
 * <p>An unauthenticated caller, or one who is not a member of the organization, is permitted, the
 * same way {@link RealmRoleMembershipPolicy} permits them: whether such a caller may reach the
 * workflow at all is the organization guard's question, not this port's, and answering it here as
 * well would make the two disagree.
 */
@Component
public class RealmStartAuthorizationPolicy implements StartAuthorizationPort {

    private final CurrentPrincipal principal;

    public RealmStartAuthorizationPolicy(CurrentPrincipal principal) {
        this.principal = principal;
    }

    @Override
    public boolean currentPrincipalHoldsAny(String orgKey, Set<String> entityRoleIds) {
        if (!principal.isAuthenticated() || !principal.isMemberOf(orgKey)) {
            return true;
        }
        Set<String> authorities = principal.authorities();
        return entityRoleIds.stream().anyMatch(authorities::contains);
    }
}
