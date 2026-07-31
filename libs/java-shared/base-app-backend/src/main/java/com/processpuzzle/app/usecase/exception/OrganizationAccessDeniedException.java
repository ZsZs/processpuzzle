package com.processpuzzle.app.usecase.exception;

/**
 * The principal may not act on this organization — typically an {@code orgKey} that does not match
 * the principal's {@code organization} claim. Surfaced as 403.
 */
public class OrganizationAccessDeniedException extends RuntimeException {

    public OrganizationAccessDeniedException(String orgKey) {
        super("Access denied for organization: " + orgKey);
    }
}
