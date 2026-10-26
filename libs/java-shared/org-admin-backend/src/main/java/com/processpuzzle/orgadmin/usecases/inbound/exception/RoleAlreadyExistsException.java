package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** The organization already declares this role. Surfaced as 409. */
public class RoleAlreadyExistsException extends RuntimeException {
    public RoleAlreadyExistsException(String directory, String name) {
        super("Organization '" + directory + "' already declares a role '" + name + "'.");
    }
}
