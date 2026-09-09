package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.DocumentTranslationKey;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoveDocumentTranslationTest {

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
    void takesTheDraftAndTheSnapshotWithIt() {
        stubFound(DocumentRoles.unrestricted());
        when(draftRepository.existsByOrgKeyAndDocumentIdAndLocale(ORG, ID, "de")).thenReturn(true);

        remove(TestPolicies.permitAll()).execute(ORG, ID, "de");

        verify(publishedRepository).deleteByOrgKeyAndDocumentIdAndLocale(ORG, ID, "de");
        verify(draftRepository).deleteById(new DocumentTranslationKey(ORG, ID, "de"));
    }

    @Test
    void theSourceLocaleIsRefused() {
        // Every other translation's basedOnRevision is measured against it and readers fall back to
        // it, so removing it would leave both without an answer. Change sourceLocale first.
        stubFound(DocumentRoles.unrestricted());

        assertThatThrownBy(() -> remove(TestPolicies.permitAll()).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentPublishingConflictException.class)
                .hasMessageContaining("change sourceLocale first");
        verify(draftRepository, never()).deleteById(any());
    }

    @Test
    void anUntranslatedLocaleIsNotFound() {
        stubFound(DocumentRoles.unrestricted());
        when(draftRepository.existsByOrgKeyAndDocumentIdAndLocale(ORG, ID, "fr")).thenReturn(false);

        assertThatThrownBy(() -> remove(TestPolicies.permitAll()).execute(ORG, ID, "fr"))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
        verify(publishedRepository, never()).deleteByOrgKeyAndDocumentIdAndLocale(any(), any(), any());
    }

    @Test
    void removingATranslationRequiresAnEditorRole() {
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()));

        assertThatThrownBy(() -> remove(TestPolicies.holding("reader")).execute(ORG, ID, "de"))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> remove(TestPolicies.permitAll()).execute(ORG, "missing", "de"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private RemoveDocumentTranslation remove(DocumentAccessPolicy policy) {
        return new RemoveDocumentTranslation(repository, draftRepository, publishedRepository,
                TestGuards.with(policy));
    }

    private void stubFound(DocumentRoles roles) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
    }
    // endregion
}
