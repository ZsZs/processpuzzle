package com.processpuzzle.app.usecase.exception;

/**
 * An operation named an {@code orgKey} that {@code TenantDirectory} does not know.
 *
 * <p>base-app's own type rather than platform-admin's {@code OrganizationNotFoundException}: this
 * module no longer compiles against the module that owns tenants, and an exception class is as much
 * a compile dependency as any other. {@code AppApiExceptionHandler} maps it to the unchanged
 * {@code organization.not-found} errorId and 404, so the contract a client sees is identical.
 */
public class UnknownTenantException extends RuntimeException {

    public UnknownTenantException(String orgKey) {
        super("Organization not found: " + orgKey);
    }
}
