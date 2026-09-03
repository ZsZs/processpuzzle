package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** The username or email is already taken in this realm. Surfaced as 409. */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String realm, String username) {
        super("Organization '" + realm + "' already has a user '" + username + "'.");
    }
}
