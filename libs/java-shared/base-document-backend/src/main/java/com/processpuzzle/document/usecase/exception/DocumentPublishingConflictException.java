package com.processpuzzle.document.usecase.exception;

import java.util.List;

/**
 * A publishing operation cannot proceed in the document's current state — 409. Covers the three
 * refusals that are about state rather than permission: publishing content that does not validate,
 * discarding a draft with nothing published to fall back to, and removing the source locale every
 * other translation is based on.
 */
public class DocumentPublishingConflictException extends RuntimeException {

    public DocumentPublishingConflictException(String message) {
        super(message);
    }

    public static DocumentPublishingConflictException notPublishable(String documentId, String locale, List<?> problems) {
        return new DocumentPublishingConflictException(
                "Locale '" + locale + "' of document '" + documentId + "' has validation errors and cannot be"
                        + " published: " + problems);
    }

    public static DocumentPublishingConflictException nothingToRevertTo(String documentId, String locale) {
        return new DocumentPublishingConflictException(
                "Locale '" + locale + "' of document '" + documentId + "' has never been published, so there is no"
                        + " published content to discard the draft in favour of.");
    }

    public static DocumentPublishingConflictException sourceLocaleNotRemovable(String documentId, String locale) {
        return new DocumentPublishingConflictException(
                "Locale '" + locale + "' is the source locale of document '" + documentId + "'. Every other"
                        + " translation is based on it and readers fall back to it, so change sourceLocale first.");
    }
}
