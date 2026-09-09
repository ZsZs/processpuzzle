package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * No tenant answers to this {@code orgKey}. Surfaced as 404 {@code organization.not-found}, per
 * org-admin-api.yaml.
 *
 * <p>This module's own type, raised when {@link
 * com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory#find} comes back empty. It
 * replaces {@code platform-admin}'s {@code OrganizationNotFoundException}, which org-admin used to
 * import for the same purpose — an exception class travelling between two feature libraries is a
 * compile edge like any other, and this one was the last thing keeping org-admin from being
 * deployable on its own.
 *
 * <p>Named for the condition rather than after the other module's class, which also avoids two
 * types with one simple name in a single build. It sits beside {@link UnknownRoleException}, and
 * the two read the same way on purpose: something the request named does not exist.
 *
 * <p>The wire contract is unchanged — {@code OrgAdminApiExceptionHandler} still answers
 * {@code organization.not-found}, because the error id belongs to the API and not to the class.
 */
public class UnknownOrganizationException extends RuntimeException {

    public UnknownOrganizationException(String orgKey) {
        super("Organization '" + orgKey + "' does not exist.");
    }
}
