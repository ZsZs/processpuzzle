package com.processpuzzle.document.usecase.exception;

public class DocumentAlreadyExistsException extends RuntimeException {

    public DocumentAlreadyExistsException(String orgKey, String documentId) {
        super("Document '" + documentId + "' already exists in organization '" + orgKey + "'");
    }
}
