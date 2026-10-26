package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * The organization declares no such role. Surfaced as 404 on the single-role operations; a role
 * <em>assignment</em> that names one is {@link UnknownRoleException}, a 400.
 */
public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException(String directory, String name) {
        super("Organization '" + directory + "' declares no role '" + name + "'.");
    }
}
