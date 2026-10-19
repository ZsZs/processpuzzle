package com.processpuzzle.document.usecase.exception;

/** No file store is wired, so a signed PDF cannot be kept — 503. */
public class DocumentFileStoreUnavailableException extends RuntimeException {

    public DocumentFileStoreUnavailableException(String message) {
        super(message);
    }
}
