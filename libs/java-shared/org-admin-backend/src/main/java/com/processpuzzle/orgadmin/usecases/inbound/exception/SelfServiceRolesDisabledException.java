package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** Self-service role selection is not enabled for this deployment. Surfaced as 404. */
public class SelfServiceRolesDisabledException extends RuntimeException {

    public SelfServiceRolesDisabledException(String message) {
        super(message);
    }
}
