package com.processpuzzle.document.usecase.exception;

/** No translation in this locale, or none published when a published one was asked for — 404. */
public class DocumentTranslationNotFoundException extends RuntimeException {

    public DocumentTranslationNotFoundException(String orgKey, String documentId, String locale) {
        super("Document '" + documentId + "' has no translation for locale '" + locale
                + "' in organization '" + orgKey + "'");
    }

    public static DocumentTranslationNotFoundException unpublished(String documentId, String locale) {
        return new DocumentTranslationNotFoundException(
                "Document '" + documentId + "' has no published content for locale '" + locale + "'");
    }

    private DocumentTranslationNotFoundException(String message) {
        super(message);
    }
}
