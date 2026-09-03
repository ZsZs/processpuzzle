package com.processpuzzle.platformadmin.usecase.exception;

/**
 * The identity provider could not be reached, or refused the operation.
 *
 * <p>Surfaced as 503 rather than 500 because it is retryable and not the platform's own fault, and
 * because the distinction matters to the caller: a 503 from {@code assignOrganizationAdmin} means the
 * user was <em>not</em> created and the same request can simply be sent again.
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }
}
