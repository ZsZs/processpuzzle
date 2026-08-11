package com.processpuzzle.document.usecase.exception;

public class DocumentBlockNotFoundException extends RuntimeException {

    public DocumentBlockNotFoundException(String orgKey, String documentId, String blockId) {
        super("No block '" + blockId + "' in document '" + documentId + "' (organization '" + orgKey + "')");
    }
}
