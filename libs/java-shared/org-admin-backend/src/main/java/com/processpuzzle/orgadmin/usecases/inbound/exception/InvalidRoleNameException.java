package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** A role name that is malformed, or that the platform or the identity provider owns. Surfaced as 400. */
public class InvalidRoleNameException extends RuntimeException {
    public InvalidRoleNameException(String name, String reason) {
        super("Role name '" + name + "' is not allowed: " + reason);
    }
}
