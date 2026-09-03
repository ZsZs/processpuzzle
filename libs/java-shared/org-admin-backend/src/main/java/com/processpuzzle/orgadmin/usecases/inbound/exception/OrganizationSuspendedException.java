package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * The tenant is suspended, so its realm is disabled and its directory must not be administered.
 * Surfaced as 404, per org-admin-api.yaml.
 *
 * <p>404 rather than 403 deliberately: a suspended tenant's administrator has no standing to be told
 * "forbidden" — from their side the organization has simply stopped existing, and 403 would invite a
 * retry that can never succeed. Only {@code platform-admin} can lift it.
 */
public class OrganizationSuspendedException extends RuntimeException {

    public OrganizationSuspendedException(String orgKey) {
        super("Organization '" + orgKey + "' is suspended.");
    }
}
