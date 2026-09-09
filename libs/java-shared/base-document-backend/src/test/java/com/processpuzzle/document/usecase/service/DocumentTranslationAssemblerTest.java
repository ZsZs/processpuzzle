package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.DocumentTranslationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentTranslationAssemblerTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private DocumentTranslationAssembler assembler;

    @BeforeEach
    void setUp() {
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        assembler = new DocumentTranslationAssembler(draftRepository, publishedRepository);
    }

    @Test
    void statesComeBackSourceLocaleFirstThenAlphabetically() {
        // Not cosmetic: the design UI's locale selector opens on the first entry, and the source
        // language is what an editor overwhelmingly wants.
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID))
                .thenReturn(List.of(draft("fr", 1L), draft("de", 1L), draft("en", null)));

        assertThat(assembler.statesOf(document()))
                .extracting(DocumentTranslationView::locale).containsExactly("en", "de", "fr");
    }

    @Test
    void eachStateIsPairedWithItsOwnSnapshotAndMeasuredAgainstTheSourceRevision() {
        DocumentDraft source = draft("en", null);
        source.replaceBlocks(List.of(text("intro"), text("outro")));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(source, draft("de", 1L)));
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(snapshot("en", 2L)));

        List<DocumentTranslationView> states = assembler.statesOf(document());

        assertThat(states.get(0).status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(states.get(0).outOfDate()).isFalse();
        assertThat(states.get(1).status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(states.get(1).outOfDate()).isTrue();
    }

    @Test
    void aDocumentWhoseSourceLocaleHasNoDraftMeasuresStalenessAgainstNothing() {
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(draft("de", 1L)));

        assertThat(assembler.statesOf(document())).singleElement()
                .satisfies(view -> assertThat(view.outOfDate()).isFalse());
    }

    @Test
    void contentOfAnswersTheDraftOrTheSnapshotAsAsked() {
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.of(draft("en", null)));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.of(snapshot("en", 1L)));

        assertThat(assembler.contentOf(document(), "en", true)).get()
                .satisfies(view -> assertThat(view.content().blocks())
                        .extracting(DocumentBlock::id).containsExactly("intro"));
        assertThat(assembler.contentOf(document(), "en", false)).get()
                .satisfies(view -> assertThat(view.content().blocks())
                        .extracting(DocumentBlock::id).containsExactly("published"));
    }

    @Test
    void nothingToServeIsEmptyRatherThanADistinctionTheCallerHasNoUseFor() {
        // Either the locale has no translation at all, or it has never been published; a caller
        // asking for published content has no use for the news that a draft exists.
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.of(draft("en", null)));

        assertThat(assembler.contentOf(document(), "en", false)).isEmpty();
        assertThat(assembler.contentOf(document(), "fr", true)).isEmpty();
    }

    @Test
    void thePublishedLocalesAreListedSourceFirstThenAlphabetically() {
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID))
                .thenReturn(List.of(snapshot("fr", 1L), snapshot("de", 1L), snapshot("en", 1L)));

        assertThat(assembler.publishedLocalesOf(document())).containsExactly("en", "de", "fr");
    }

    @Test
    void aDocumentWithNothingPublishedHasNoPublishedLocales() {
        assertThat(assembler.publishedLocalesOf(document())).isEmpty();
    }

    @Test
    void theSourceRevisionIsReadOnceAndIsNullWhenThereIsNoSourceDraft() {
        assertThat(assembler.sourceRevisionOf(document())).isNull();

        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.of(draft("en", null)));

        assertThat(assembler.sourceRevisionOf(document())).isEqualTo(1L);
    }

    // region fixtures
    private static Document document() {
        return new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
    }

    private static DocumentDraft draft(String locale, Long basedOnRevision) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(List.of(text("intro"))), basedOnRevision);
    }

    private static PublishedDocument snapshot(String locale, long publishedRevision) {
        return new PublishedDocument(ORG, ID, locale, DocumentContent.of(List.of(text("published"))),
                publishedRevision, Instant.parse("2026-01-02T03:04:05Z"), "ada");
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
