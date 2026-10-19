package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.ContentBookNode;
import com.processpuzzle.document.domain.ContentBookNodes;
import com.processpuzzle.document.domain.ContentBookRepository;
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
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ContentBookRepository contentBookRepository;
    private final List<PlatformEvent> facts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        contentBookRepository = mock(ContentBookRepository.class);
        when(contentBookRepository.findByOrgKeyReferencing(any(), any())).thenReturn(List.of());
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
    void publishesADeletedFactWithAnEmptyPayload() {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document(DocumentRoles.unrestricted())));

        deleteDocument(TestPolicies.permitAll()).execute(ORG, ID);

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.action()).isEqualTo(PlatformEventAction.DELETED);
            assertThat(fact.orgKey()).isEqualTo(ORG);
            assertThat(fact.subjectType()).isEqualTo("document");
            assertThat(fact.subjectId()).isEqualTo(ID);
            assertThat(fact.payload()).isEmpty();
        });
    }

    @Test
    void detachesTheDocumentFromEveryBookThatShowsIt() {
        // The pages filed under the deleted one move up into its place instead of vanishing with it.
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document(DocumentRoles.unrestricted())));
        ContentBook book = new ContentBook(ORG, "book-1", "handbook", "Handbook", "ada");
        book.replaceNodes(new ContentBookNodes(List.of(
                new ContentBookNode("root", ID, null, 0),
                new ContentBookNode("child", "22222222-2222-2222-2222-222222222222", "root", 0))));
        when(contentBookRepository.findByOrgKeyReferencing(ORG, ID)).thenReturn(List.of(book));

        deleteDocument(TestPolicies.permitAll()).execute(ORG, ID);

        assertThat(book.getNodes().nodes()).singleElement().satisfies(node -> {
            assertThat(node.id()).isEqualTo("child");
            assertThat(node.parentId()).isNull();
        });
        verify(contentBookRepository).save(book);
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
        assertThat(facts).isEmpty();
    }

    // region fixtures
    private DeleteDocument deleteDocument(DocumentAccessPolicy policy) {
        return new DeleteDocument(repository, draftRepository, publishedRepository, contentBookRepository,
                TestGuards.with(policy),
                TestFacts.recordingInto(facts));
    }

    private static Document document(DocumentRoles roles) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        return document;
    }
    // endregion
}
