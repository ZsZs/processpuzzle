package com.processpuzzle.document.usecase.exception;

/** Mapped to 404 — also what a principal who may not read the book is told, as for documents. */
public class ContentBookNotFoundException extends RuntimeException {

    public ContentBookNotFoundException(String orgKey, String contentBookId) {
        super("No content book '" + contentBookId + "' in organization '" + orgKey + "'");
    }
}
