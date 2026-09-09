package com.processpuzzle.document.usecase.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String orgKey, String documentId) {
        super("No document '" + documentId + "' in organization '" + orgKey + "'");
    }
}
