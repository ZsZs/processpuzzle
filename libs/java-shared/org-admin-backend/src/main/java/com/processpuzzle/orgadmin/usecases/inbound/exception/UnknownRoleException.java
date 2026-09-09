package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * A role name that the tenant's realm does not declare. Surfaced as 400.
 *
 * <p>Refused rather than created. Silently minting the role would make a typo in a role assignment
 * produce a role that nothing in the platform ever matches — invisible until someone wondered why a
 * user's permissions did not take effect.
 */
public class UnknownRoleException extends RuntimeException {

    public UnknownRoleException(String realm, String roleName) {
        super("Organization '" + realm + "' declares no role '" + roleName + "'.");
    }
}
