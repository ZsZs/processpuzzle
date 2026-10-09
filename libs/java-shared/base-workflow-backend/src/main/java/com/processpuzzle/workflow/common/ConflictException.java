package com.processpuzzle.workflow.common;

// Note: same note as NotFoundException — replace with processpuzzle-core's equivalent.
public class ConflictException extends RuntimeException {

    private final String errorId;

    public ConflictException(String message) {
        this("workflow.conflict", message);
    }

    /** For a conflict a client tells apart from the generic one by its {@code errorId}. */
    public ConflictException(String errorId, String message) {
        super(message);
        this.errorId = errorId;
    }

    public String getErrorId() {
        return errorId;
    }
}
