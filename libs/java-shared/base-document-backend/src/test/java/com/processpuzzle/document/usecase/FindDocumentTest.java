package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindDocumentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
    }

    @Test
    void withoutALocaleTheSourceLocalesContentIsWhatComesBack() {
        Document document = document(DocumentRoles.unrestricted(), false);
        stub(document, draft("en", "intro"), null);

        DocumentDetails details = findDocument(TestPolicies.permitAll()).execute(ORG, ID, null, true);

        assertThat(details.selected().locale()).isEqualTo("en");
        assertThat(details.selected().content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void anExplicitLocaleWins() {
        Document document = document(DocumentRoles.unrestricted(), false);
        stub(document, draft("de", "einleitung"), null);

        DocumentDetails details = findDocument(TestPolicies.permitAll()).execute(ORG, ID, "de", true);

        assertThat(details.selected().locale()).isEqualTo("de");
    }

    @Test
    void askingForTheDraftRequiresAnEditorRole() {
        // The only authenticated path that can return unpublished content, so the check lives here.
        Document document = document(new DocumentRoles(List.of(), List.of("editor"), List.of()), false);
        stub(document, draft("en", "intro"), null);

        assertThatThrownBy(() -> findDocument(TestPolicies.holding("reader")).execute(ORG, ID, "en", true))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThat(findDocument(TestPolicies.holding("editor")).execute(ORG, ID, "en", true).selected()).isNotNull();
    }

    @Test
    void aReaderWhoMayNotSeeTheDocumentIsToldItDoesNotExist() {
        // 404 rather than 403: telling them it exists is exactly what a private document is for.
        Document document = document(new DocumentRoles(List.of("insider"), List.of(), List.of()), false);
        stub(document, draft("en", "intro"), null);

        assertThatThrownBy(() -> findDocument(TestPolicies.holding("outsider")).execute(ORG, ID, "en", false))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void thePublishedReadReturnsTheSnapshotRatherThanTheDraft() {
        Document document = document(DocumentRoles.unrestricted(), true);
        stub(document, draft("en", "edited"), snapshot("published"));

        DocumentDetails details = findDocument(TestPolicies.permitAll()).execute(ORG, ID, "en", false);

        assertThat(details.selected().content().blocks()).extracting(DocumentBlock::id).containsExactly("published");
    }

    @Test
    void aLocaleWithNothingToServeSelectsNothingRatherThanFailing() {
        Document document = document(DocumentRoles.unrestricted(), true);
        stub(document, draft("en", "intro"), null);

        DocumentDetails details = findDocument(TestPolicies.permitAll()).execute(ORG, ID, "fr", false);

        assertThat(details.selected()).isNull();
        assertThat(details.states()).extracting(DocumentTranslationView::locale).containsExactly("en");
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findDocument(TestPolicies.permitAll()).execute(ORG, "missing", null, false))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private FindDocument findDocument(DocumentAccessPolicy policy) {
        return new FindDocument(repository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy));
    }

    private void stub(Document document, DocumentDraft draft, PublishedDocument published) {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(draft));
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                .thenReturn(Optional.of(draft));
        if (published != null) {
            when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(published));
            when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, published.getLocale()))
                    .thenReturn(Optional.of(published));
        }
    }

    private static Document document(DocumentRoles roles, boolean isPublic) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", isPublic, roles,
                DocumentPorts.empty());
        return document;
    }

    private static DocumentDraft draft(String locale, String blockId) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(List.of(text(blockId))), null);
    }

    private static PublishedDocument snapshot(String blockId) {
        return new PublishedDocument(ORG, ID, "en", DocumentContent.of(List.of(text(blockId))), 1L,
                Instant.parse("2026-01-02T03:04:05Z"), "ada");
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
