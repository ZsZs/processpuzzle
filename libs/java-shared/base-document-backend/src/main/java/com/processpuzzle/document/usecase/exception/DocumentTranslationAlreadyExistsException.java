package com.processpuzzle.document.usecase.exception;

/** A translation already exists for this locale — 409. */
public class DocumentTranslationAlreadyExistsException extends RuntimeException {

    public DocumentTranslationAlreadyExistsException(String documentId, String locale) {
        super("Document '" + documentId + "' already has a translation for locale '" + locale + "'");
    }
}
