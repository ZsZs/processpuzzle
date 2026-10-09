package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Set;

/**
 * Development stand-in that admits every start, used when the deploying application supplies no
 * {@link StartAuthorizationPort} bean — the counterpart of {@link PermitAllRoleMembershipPort}, with
 * the same reasoning and the same caveat.
 *
 * <p>Deliberately not a {@code @Component}: {@code StartEventAdmission} instantiates it as a
 * fallback via {@code ObjectProvider#getIfUnique}.
 */
public class PermitAllStartAuthorizationPort implements StartAuthorizationPort {

    @Override
    public boolean currentPrincipalHoldsAny(String orgKey, Set<String> entityRoleIds) {
        return true;
    }
}
