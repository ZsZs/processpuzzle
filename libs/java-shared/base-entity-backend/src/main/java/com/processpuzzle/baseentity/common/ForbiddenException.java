package com.processpuzzle.baseentity.common;

/** Thrown when the caller may see a resource but not change it: a member writing a shared record, for one. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
