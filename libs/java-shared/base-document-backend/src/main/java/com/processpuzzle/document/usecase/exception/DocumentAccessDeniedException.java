package com.processpuzzle.document.usecase.exception;

/**
 * The principal may not perform this operation on this document — mapped to 403.
 *
 * <p>Note what this is <em>not</em> used for: a reader who may not see a document at all. That case
 * answers 404 instead, so the existence of restricted content does not leak to someone who cannot
 * read it. 403 is for a caller who demonstrably knows the document exists — an editor without
 * publish rights, say.
 */
public class DocumentAccessDeniedException extends RuntimeException {

    public DocumentAccessDeniedException(String message) {
        super(message);
    }

    public static DocumentAccessDeniedException lacksRole(String action, String documentId) {
        return new DocumentAccessDeniedException(
                "The current principal may not " + action + " document '" + documentId + "'");
    }
}
