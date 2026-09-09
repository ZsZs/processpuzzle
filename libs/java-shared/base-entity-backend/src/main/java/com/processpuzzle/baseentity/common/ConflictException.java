package com.processpuzzle.baseentity.common;

/**
 * Exception thrown when an operation conflicts with existing entity state or constraints.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
