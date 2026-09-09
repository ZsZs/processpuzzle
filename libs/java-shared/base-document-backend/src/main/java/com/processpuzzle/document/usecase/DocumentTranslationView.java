package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;

import java.time.Instant;

/**
 * One translation as a reader of the API sees it: the selected content plus the publication state
 * derived from the draft/snapshot pair.
 *
 * <p>Exists because that state is spread across two entities by design — {@link DocumentDraft}
 * knows the current revision, {@link PublishedDocument} knows which revision is being served, and
 * neither can compute {@link DocumentStatus} alone. Pairing them here keeps that join in one place
 * instead of leaving every caller and the mapper to remember which of the two to ask for what, and
 * keeps the mapper ignorant of whether it was handed a draft or a snapshot.
 *
 * @param content    the content actually selected — the draft's or the snapshot's, per the request;
 *                   {@code null} in the state-only projection
 * @param blockCount carried separately from {@code content} so the state-only projection can report
 *                   a size without holding the blocks
 * @param revision   the draft's current revision, even when published content was selected, since
 *                   that is what {@code status} is derived against
 * @param outOfDate  whether the source locale has moved on since this translation was made
 */
public record DocumentTranslationView(
        String locale,
        DocumentContent content,
        int blockCount,
        Long revision,
        Long publishedRevision,
        Long basedOnRevision,
        boolean outOfDate,
        Instant publishedAt,
        Instant updatedAt) {

    public DocumentStatus status() {
        return DocumentStatus.derive(revision, publishedRevision);
    }

    /** The editable content of a translation, with the publication state it is measured against. */
    public static DocumentTranslationView ofDraft(DocumentDraft draft, PublishedDocument published, Long sourceRevision) {
        return new DocumentTranslationView(
                draft.getLocale(),
                draft.getContent(),
                draft.getBlocks().size(),
                draft.getRevision(),
                published == null ? null : published.getPublishedRevision(),
                draft.getBasedOnRevision(),
                draft.isOutOfDate(sourceRevision),
                published == null ? null : published.getPublishedAt(),
                draft.getUpdatedAt());
    }

    /**
     * The published snapshot. The draft is still consulted — for the revision the status is derived
     * from, and for staleness — but its content is not read, which is what "published" has to mean
     * for the guarantee to hold.
     */
    public static DocumentTranslationView ofPublished(PublishedDocument published, DocumentDraft draft, Long sourceRevision) {
        return new DocumentTranslationView(
                published.getLocale(),
                published.getContent(),
                published.getBlocks().size(),
                draft == null ? published.getPublishedRevision() : draft.getRevision(),
                published.getPublishedRevision(),
                draft == null ? null : draft.getBasedOnRevision(),
                draft != null && draft.isOutOfDate(sourceRevision),
                published.getPublishedAt(),
                published.getPublishedAt());
    }

    /**
     * State without content, for the list and locale-selector views. Content is genuinely dropped
     * rather than left in and ignored, so a caller that only asked for state cannot accidentally
     * serialize a whole block list — which is what lets {@code listDocuments} afford to return
     * every other field.
     */
    public static DocumentTranslationView stateOnly(DocumentDraft draft, PublishedDocument published, Long sourceRevision) {
        return new DocumentTranslationView(
                draft.getLocale(),
                null,
                draft.getBlocks().size(),
                draft.getRevision(),
                published == null ? null : published.getPublishedRevision(),
                draft.getBasedOnRevision(),
                draft.isOutOfDate(sourceRevision),
                published == null ? null : published.getPublishedAt(),
                draft.getUpdatedAt());
    }
}
