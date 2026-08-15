package com.processpuzzle.baseentity.common;

// TODO: same note as NotFoundException — replace with processpuzzle-core's equivalent.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
