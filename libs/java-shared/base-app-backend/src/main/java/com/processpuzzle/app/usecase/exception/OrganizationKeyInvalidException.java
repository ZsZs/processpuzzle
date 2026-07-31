package com.processpuzzle.app.usecase.exception;

/**
 * The requested organization key is malformed or reserved for the platform's own routes.
 * Surfaced as 400.
 */
public class OrganizationKeyInvalidException extends RuntimeException {

    private final String errorId;

    public OrganizationKeyInvalidException(String errorId, String message) {
        super(message);
        this.errorId = errorId;
    }

    /** Stable identifier the sign-up form can use as a Transloco key. */
    public String getErrorId() {
        return errorId;
    }
}
