package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** Self-registration is not enabled for this deployment. Surfaced as 404. */
public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException(String message) {
        super(message);
    }
}
