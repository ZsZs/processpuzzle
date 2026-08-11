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
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindDocumentTranslationsTest {

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
    void listsEveryLocaleWithTheSourceOneFirst() {
        stubFound(DocumentRoles.unrestricted(), draft("de"), draft("en"));

        assertThat(findTranslations(TestPolicies.permitAll()).executeAll(ORG, ID))
                .extracting(DocumentTranslationView::locale).containsExactly("en", "de");
    }

    @Test
    void oneLocalesDraftContentCanBeReadOnItsOwn() {
        stubFound(DocumentRoles.unrestricted(), draft("en"));

        DocumentTranslationView view = findTranslations(TestPolicies.permitAll()).executeOne(ORG, ID, "en", true);

        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void onlyAnEditorMayAskForTheDraft() {
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()), draft("en"));

        assertThatThrownBy(() -> findTranslations(TestPolicies.holding("reader")).executeOne(ORG, ID, "en", true))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThat(findTranslations(TestPolicies.holding("editor")).executeOne(ORG, ID, "en", true)).isNotNull();
    }

    @Test
    void aLocaleWithNothingToServeIsNotFound() {
        // Never published and asked for as published: nothing to serve, so 404 rather than an empty body.
        stubFound(DocumentRoles.unrestricted(), draft("en"));

        assertThatThrownBy(() -> findTranslations(TestPolicies.permitAll()).executeOne(ORG, ID, "en", false))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
    }

    @Test
    void aDocumentTheReaderMayNotSeeIsReportedAsMissingOnBothReads() {
        stubFound(new DocumentRoles(List.of("insider"), List.of(), List.of()), draft("en"));

        assertThatThrownBy(() -> findTranslations(TestPolicies.holding("outsider")).executeAll(ORG, ID))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> findTranslations(TestPolicies.holding("outsider")).executeOne(ORG, ID, "en", false))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findTranslations(TestPolicies.permitAll()).executeAll(ORG, "missing"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private FindDocumentTranslations findTranslations(DocumentAccessPolicy policy) {
        return new FindDocumentTranslations(repository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy));
    }

    private void stubFound(DocumentRoles roles, DocumentDraft... drafts) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(drafts));
        for (DocumentDraft draft : drafts) {
            when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                    .thenReturn(Optional.of(draft));
        }
    }

    private static DocumentDraft draft(String locale) {
        return new DocumentDraft(ORG, ID, locale,
                DocumentContent.of(List.of(new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null,
                        null, null, null))), null);
    }
    // endregion
}
