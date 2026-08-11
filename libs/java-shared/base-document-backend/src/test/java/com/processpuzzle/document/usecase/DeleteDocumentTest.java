package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentKey;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteDocumentTest {

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
    void removesTheContentBeforeTheRecordThatGivesItMeaning() {
        // A failure part-way must not leave published content addressable by a slug whose document
        // is already gone, so the order is snapshots, drafts, then the document.
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document(DocumentRoles.unrestricted())));

        deleteDocument(TestPolicies.permitAll()).execute(ORG, ID);

        InOrder order = inOrder(publishedRepository, draftRepository, repository);
        order.verify(publishedRepository).deleteByOrgKeyAndDocumentId(ORG, ID);
        order.verify(draftRepository).deleteByOrgKeyAndDocumentId(ORG, ID);
        order.verify(repository).deleteById(new DocumentKey(ORG, ID));
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteDocument(TestPolicies.permitAll()).execute(ORG, "missing"))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deletingRequiresAnEditorRole() {
        when(repository.findByOrgKeyAndId(ORG, ID))
                .thenReturn(Optional.of(document(new DocumentRoles(List.of(), List.of("editor"), List.of()))));

        assertThatThrownBy(() -> deleteDocument(TestPolicies.holding("reader")).execute(ORG, ID))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(publishedRepository, never()).deleteByOrgKeyAndDocumentId(any(), any());
        verify(draftRepository, never()).deleteByOrgKeyAndDocumentId(any(), any());
        verify(repository, never()).deleteById(any());
    }

    // region fixtures
    private DeleteDocument deleteDocument(DocumentAccessPolicy policy) {
        return new DeleteDocument(repository, draftRepository, publishedRepository, TestGuards.with(policy));
    }

    private static Document document(DocumentRoles roles) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        return document;
    }
    // endregion
}
