package com.processpuzzle.document.usecase.exception;

import java.util.List;

/**
 * A publishing operation cannot proceed in the document's current state — 409. Covers the three
 * refusals that are about state rather than permission: publishing content that does not validate,
 * discarding a draft with nothing published to fall back to, and removing the source locale every
 * other translation is based on.
 *
 * <p>All three share a status, so each carries its own {@code errorId} for
 * {@code DocumentApiExceptionHandler} to put on the wire. A client that has to tell "fix the content
 * first" from "there is nothing to revert to" can only do so from the id — the status cannot express it
 * and the message is prose.
 */
public class DocumentPublishingConflictException extends RuntimeException {

    private final String errorId;

    public DocumentPublishingConflictException(String errorId, String message) {
        super(message);
        this.errorId = errorId;
    }

    public String getErrorId() {
        return errorId;
    }

    public static DocumentPublishingConflictException notPublishable(String documentId, String locale, List<?> problems) {
        return new DocumentPublishingConflictException("document.publish.not-publishable",
                "Locale '" + locale + "' of document '" + documentId + "' has validation errors and cannot be"
                        + " published: " + problems);
    }

    public static DocumentPublishingConflictException nothingToRevertTo(String documentId, String locale) {
        return new DocumentPublishingConflictException("document.draft.nothing-to-revert-to",
                "Locale '" + locale + "' of document '" + documentId + "' has never been published, so there is no"
                        + " published content to discard the draft in favour of.");
    }

    /** Id matches the Cloud Function's, which answers the same refusal — see base-document.handlers.ts. */
    public static DocumentPublishingConflictException sourceLocaleNotRemovable(String documentId, String locale) {
        return new DocumentPublishingConflictException("document.translation.source-locale-not-removable",
                "Locale '" + locale + "' is the source locale of document '" + documentId + "'. Every other"
                        + " translation is based on it and readers fall back to it, so change sourceLocale first.");
    }
}
