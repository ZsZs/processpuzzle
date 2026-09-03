package com.processpuzzle.platformadmin.usecase.exception;

/** The requested organization key is already claimed. Surfaced as 409. */
public class OrganizationAlreadyExistsException extends RuntimeException {

    public OrganizationAlreadyExistsException(String orgKey) {
        super("Organization key already taken: " + orgKey);
    }
}
