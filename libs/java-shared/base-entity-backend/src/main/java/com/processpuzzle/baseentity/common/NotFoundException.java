package com.processpuzzle.baseentity.common;

/**
 * Exception thrown when a requested entity definition or object cannot be found.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
