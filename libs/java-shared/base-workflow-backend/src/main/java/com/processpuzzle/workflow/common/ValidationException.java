package com.processpuzzle.workflow.common;

// TODO: same note as NotFoundException — replace with processpuzzle-core's equivalent.
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
