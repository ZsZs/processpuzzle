package com.processpuzzle.core.identity;

import org.springframework.web.client.RestClientResponseException;

import java.util.OptionalInt;

/**
 * The identity provider could not be reached, or refused the operation.
 *
 * <p>Surfaced as 503 rather than 500 because it is retryable and not the platform's own fault, and
 * because the distinction matters to the caller: a 503 from {@code assignOrganizationAdmin} means the
 * user was <em>not</em> created and the same request can simply be sent again.
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    private final Integer httpStatus;

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = cause instanceof RestClientResponseException response
                ? response.getStatusCode().value() : null;
    }

    public IdentityProviderUnavailableException(String message) {
        this(message, null);
    }

    /** The actual response status, absent for transport failures or failures without a response. */
    public OptionalInt getHttpStatus() {
        return httpStatus == null ? OptionalInt.empty() : OptionalInt.of(httpStatus);
    }
}
