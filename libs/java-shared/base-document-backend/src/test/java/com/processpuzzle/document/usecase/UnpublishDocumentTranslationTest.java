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
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.event.DocumentUnpublished;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnpublishDocumentTranslationTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private List<Object> events;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        events = new ArrayList<>();
    }

    @Test
    void discardsTheSnapshotAndLeavesTheDraftAlone() {
        // Deleting rather than flagging is what keeps "withdrawn" from being a third stored state:
        // with no snapshot, status derives back to DRAFT and the public path simply finds nothing.
        DocumentDraft draft = draft();
        stubFound(DocumentRoles.unrestricted(), draft);

        DocumentTranslationView view = unpublish(TestPolicies.permitAll()).execute(ORG, ID, "en");

        verify(publishedRepository).deleteByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en");
        verify(draftRepository, never()).deleteById(any());
        assertThat(view.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(view.publishedRevision()).isNull();
        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void unpublishingSomethingAlreadyUnpublishedIsNotAnError() {
        // The caller's intent is "this must not be public", and it already is not.
        stubFound(DocumentRoles.unrestricted(), draft());

        assertThat(unpublish(TestPolicies.permitAll()).execute(ORG, ID, "en").status())
                .isEqualTo(DocumentStatus.DRAFT);
        assertThat(unpublish(TestPolicies.permitAll()).execute(ORG, ID, "en").status())
                .isEqualTo(DocumentStatus.DRAFT);
        assertThat(events).hasSize(2);
    }

    @Test
    void emitsDocumentUnpublishedWithTheSlugAReaderWouldHaveUsed() {
        stubFound(DocumentRoles.unrestricted(), draft());

        unpublish(TestPolicies.principal("ada")).execute(ORG, ID, "en");

        assertThat(events).singleElement().isInstanceOf(DocumentUnpublished.class);
        DocumentUnpublished event = (DocumentUnpublished) events.get(0);
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.documentId()).isEqualTo(ID);
        assertThat(event.slug()).isEqualTo("getting-started");
        assertThat(event.locale()).isEqualTo("en");
        assertThat(event.unpublishedBy()).isEqualTo("ada");
        assertThat(event.unpublishedAt()).isNotNull();
    }

    @Test
    void anUntranslatedLocaleIsNotFound() {
        stubFound(DocumentRoles.unrestricted(), draft());

        assertThatThrownBy(() -> unpublish(TestPolicies.permitAll()).execute(ORG, ID, "fr"))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
        assertThat(events).isEmpty();
    }

    @Test
    void unpublishingRequiresAPublisherRoleWhichFallsBackToTheEditors() {
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()), draft());

        assertThatThrownBy(() -> unpublish(TestPolicies.holding("reader")).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThat(unpublish(TestPolicies.holding("editor")).execute(ORG, ID, "en")).isNotNull();
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> unpublish(TestPolicies.permitAll()).execute(ORG, "missing", "en"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private UnpublishDocumentTranslation unpublish(DocumentAccessPolicy policy) {
        ApplicationEventPublisher publisher = event -> events.add(event);
        return new UnpublishDocumentTranslation(repository, draftRepository, publishedRepository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy), publisher);
    }

    private void stubFound(DocumentRoles roles, DocumentDraft draft) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en")).thenReturn(Optional.of(draft));
    }

    private static DocumentDraft draft() {
        return new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(
                new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null, null, null, null))), null);
    }
    // endregion
}
