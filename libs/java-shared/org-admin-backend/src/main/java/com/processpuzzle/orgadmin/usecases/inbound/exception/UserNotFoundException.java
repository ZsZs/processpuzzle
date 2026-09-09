package com.processpuzzle.orgadmin.usecases.inbound.exception;

/** No such user in this tenant's realm. Surfaced as 404. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String realm, String userId) {
        super("No user '" + userId + "' in organization '" + realm + "'.");
    }
}
