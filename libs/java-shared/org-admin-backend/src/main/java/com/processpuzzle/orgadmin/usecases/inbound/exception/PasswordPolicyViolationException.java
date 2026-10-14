package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** The identity provider refused the registration, typically because of its password policy. Surfaced as 400. */
public class PasswordPolicyViolationException extends RuntimeException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
