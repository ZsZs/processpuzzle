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
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscardDocumentDraftTest {

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
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void copiesThePublishedSnapshotBackOverTheDraftWithoutBumpingTheCounter() {
        // Discarding returns to a state that was already published rather than creating a new one, so
        // status has to derive back to PUBLISHED.
        DocumentDraft draft = draft();
        draft.replaceBlocks(List.of(text("edited")));
        stubFound(DocumentRoles.unrestricted(), draft, snapshot(1L));

        DocumentTranslationView view = discard(TestPolicies.permitAll()).execute(ORG, ID, "en");

        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("published");
        assertThat(draft.getRevision()).isEqualTo(1L);
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(draftRepository).save(draft);
    }

    @Test
    void aTranslationThatWasNeverPublishedHasNothingToRevertTo() {
        // Emptying the draft instead would destroy the only copy of the content under a name that
        // sounds like it undoes something small.
        DocumentDraft draft = draft();
        stubFound(DocumentRoles.unrestricted(), draft, null);

        assertThatThrownBy(() -> discard(TestPolicies.permitAll()).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentPublishingConflictException.class)
                .hasMessageContaining("never been published");
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("intro");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void anUntranslatedLocaleIsNotFound() {
        stubFound(DocumentRoles.unrestricted(), draft(), snapshot(1L));

        assertThatThrownBy(() -> discard(TestPolicies.permitAll()).execute(ORG, ID, "fr"))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
    }

    @Test
    void discardingADraftRequiresAPublisherRole() {
        stubFound(new DocumentRoles(List.of(), List.of(), List.of("publisher")), draft(), snapshot(1L));

        assertThatThrownBy(() -> discard(TestPolicies.holding("editor")).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThat(discard(TestPolicies.holding("publisher")).execute(ORG, ID, "en")).isNotNull();
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> discard(TestPolicies.permitAll()).execute(ORG, "missing", "en"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private DiscardDocumentDraft discard(DocumentAccessPolicy policy) {
        return new DiscardDocumentDraft(repository, draftRepository, publishedRepository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy));
    }

    private void stubFound(DocumentRoles roles, DocumentDraft draft, PublishedDocument snapshot) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en")).thenReturn(Optional.of(draft));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.ofNullable(snapshot));
    }

    private static DocumentDraft draft() {
        return new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);
    }

    private static PublishedDocument snapshot(long publishedRevision) {
        return new PublishedDocument(ORG, ID, "en", DocumentContent.of(List.of(text("published"))), publishedRevision,
                Instant.parse("2026-01-02T03:04:05Z"), "ada");
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
