package com.processpuzzle.workflow.common;

// Note: same note as NotFoundException — replace with processpuzzle-core's equivalent.
public class ForbiddenException extends RuntimeException {

    private final String errorId;

    public ForbiddenException(String errorId, String message) {
        super(message);
        this.errorId = errorId;
    }

    public String getErrorId() {
        return errorId;
    }
}
