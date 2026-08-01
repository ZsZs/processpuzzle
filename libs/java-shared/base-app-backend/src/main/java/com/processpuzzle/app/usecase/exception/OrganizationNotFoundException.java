package com.processpuzzle.app.usecase.exception;

/** No organization with this key exists. Surfaced as 404. */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(String orgKey) {
        super("Organization not found: " + orgKey);
    }
}
