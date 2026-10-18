package com.processpuzzle.document.usecase.exception;

/**
 * A content book tree that cannot be stored — mapped to 400. Its own type rather than core's generic
 * {@code IllegalArgumentException}, so a client can tell a broken tree from a malformed request.
 */
public class ContentBookInvalidTreeException extends RuntimeException {

    public ContentBookInvalidTreeException(String message) {
        super(message);
    }
}
