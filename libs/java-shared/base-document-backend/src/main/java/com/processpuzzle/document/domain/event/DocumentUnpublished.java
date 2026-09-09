package com.processpuzzle.document.domain.event;

import java.time.Instant;

/**
 * One locale of a document was withdrawn from public view. Its draft survives — see
 * {@code UnpublishDocumentTranslation} — so this is a visibility change, not a deletion.
 */
public record DocumentUnpublished(
        String orgKey,
        String documentId,
        String slug,
        String locale,
        Instant unpublishedAt,
        String unpublishedBy) {
}
