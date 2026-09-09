package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The join between a draft and its snapshot. Neither entity can compute {@link DocumentStatus}
 * alone, so this is where getting it wrong would be invisible until a published page showed the
 * wrong badge.
 */
class DocumentTranslationViewTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final Instant PUBLISHED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void anUnpublishedDraftIsSimplyADraft() {
        DocumentTranslationView view = DocumentTranslationView.ofDraft(draft("en", null), null, 1L);

        assertThat(view.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(view.publishedRevision()).isNull();
        assertThat(view.publishedAt()).isNull();
        assertThat(view.blockCount()).isEqualTo(1);
        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void aDraftEditedSinceItWasPublishedSaysSo() {
        DocumentDraft draft = draft("en", null);
        draft.replaceBlocks(List.of(text("edited")));

        DocumentTranslationView view = DocumentTranslationView.ofDraft(draft, snapshot(1L), 2L);

        assertThat(view.revision()).isEqualTo(2L);
        assertThat(view.publishedRevision()).isEqualTo(1L);
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES);
        assertThat(view.publishedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void thePublishedProjectionServesTheSnapshotButDerivesStatusFromTheDraft() {
        // Its content is the snapshot's — that is what "published" has to mean — while the draft is
        // still consulted for the revision the status is measured against.
        DocumentDraft draft = draft("en", 3L);
        draft.replaceBlocks(List.of(text("edited")));

        DocumentTranslationView view = DocumentTranslationView.ofPublished(snapshot(1L), draft, 4L);

        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("published");
        assertThat(view.revision()).isEqualTo(2L);
        assertThat(view.publishedRevision()).isEqualTo(1L);
        assertThat(view.basedOnRevision()).isEqualTo(3L);
        assertThat(view.outOfDate()).isTrue();
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES);
        assertThat(view.updatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    void aSnapshotWithNoDraftBehindItReportsItselfCurrent() {
        DocumentTranslationView view = DocumentTranslationView.ofPublished(snapshot(2L), null, 2L);

        assertThat(view.revision()).isEqualTo(2L);
        assertThat(view.basedOnRevision()).isNull();
        assertThat(view.outOfDate()).isFalse();
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED);
    }

    @Test
    void theStateOnlyProjectionDropsTheContentButKeepsTheCount() {
        // Content is genuinely dropped rather than left in and ignored, so a caller that only asked
        // for state cannot accidentally serialize a whole block list.
        DocumentTranslationView view = DocumentTranslationView.stateOnly(draft("de", 1L), snapshot(1L), 2L);

        assertThat(view.content()).isNull();
        assertThat(view.blockCount()).isEqualTo(1);
        assertThat(view.locale()).isEqualTo("de");
        assertThat(view.publishedRevision()).isEqualTo(1L);
        assertThat(view.publishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(view.outOfDate()).isTrue();
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED);
    }

    @Test
    void theStateOnlyProjectionOfAnUnpublishedLocaleHasNoPublicationFields() {
        DocumentTranslationView view = DocumentTranslationView.stateOnly(draft("de", 1L), null, 1L);

        assertThat(view.publishedRevision()).isNull();
        assertThat(view.publishedAt()).isNull();
        assertThat(view.outOfDate()).isFalse();
        assertThat(view.status()).isEqualTo(DocumentStatus.DRAFT);
    }

    // region fixtures
    private static DocumentDraft draft(String locale, Long basedOnRevision) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(List.of(text("intro"))), basedOnRevision);
    }

    private static PublishedDocument snapshot(long publishedRevision) {
        return new PublishedDocument(ORG, ID, "en", DocumentContent.of(List.of(text("published"))),
                publishedRevision, PUBLISHED_AT, "ada");
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
