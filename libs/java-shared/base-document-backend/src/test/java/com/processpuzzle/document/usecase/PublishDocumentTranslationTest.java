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
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.domain.event.DocumentPublished;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishDocumentTranslationTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private List<Object> publishedEvents;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        publishedEvents = new ArrayList<>();
    }

    @Test
    void copiesTheDraftIntoASnapshotWithoutMovingTheDraftRevision() {
        // If revision moved here, publishedRevision would immediately be behind it and a freshly
        // published translation would report unpublished edits. That is why revision is a plain
        // counter and not the @Version.
        Document document = document();
        DocumentDraft draft = draftAtRevision(3L, List.of(text("intro")));
        stub(document, draft, null);

        DocumentTranslationView view = publisher(permitAll()).execute(ORG, ID, "en");

        assertThat(draft.getRevision()).isEqualTo(3L);
        assertThat(view.publishedRevision()).isEqualTo(3L);
        assertThat(view.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void recordsTheDocumentsPublicationDateOnlyOnTheFirstPublish() {
        Document document = document();
        stub(document, draftAtRevision(1L, List.of()), null);

        publisher(permitAll()).execute(ORG, ID, "en");
        java.time.Instant first = document.getPublishedAt();
        assertThat(first).isNotNull();

        publisher(permitAll()).execute(ORG, ID, "en");

        assertThat(document.getPublishedAt()).isEqualTo(first);
    }

    @Test
    void republishingReplacesTheExistingSnapshot() {
        Document document = document();
        DocumentDraft draft = draftAtRevision(5L, List.of(text("new")));
        PublishedDocument existing = new PublishedDocument(ORG, ID, "en",
                DocumentContent.of(List.of(text("old"))), 2L, java.time.Instant.now(), "ada");
        stub(document, draft, existing);

        publisher(permitAll()).execute(ORG, ID, "en");

        assertThat(existing.getPublishedRevision()).isEqualTo(5L);
        assertThat(existing.getBlocks()).extracting(DocumentBlock::id).containsExactly("new");
    }

    @Test
    void emitsDocumentPublishedSoAnApprovalStepCanAttachLater() {
        Document document = document();
        stub(document, draftAtRevision(4L, List.of()), null);

        publisher(permitAll()).execute(ORG, ID, "en");

        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(DocumentPublished.class);
        DocumentPublished event = (DocumentPublished) publishedEvents.get(0);
        assertThat(event.slug()).isEqualTo("getting-started");
        assertThat(event.locale()).isEqualTo("en");
        assertThat(event.revision()).isEqualTo(4L);
    }

    @Test
    void refusesToPublishContentThatDoesNotValidate() {
        // A widget bound to a port that no longer exists. Publishing it would put a page in front of
        // readers that cannot render, so this is a conflict rather than a warning.
        Document document = document();
        DocumentBlock broken = new DocumentBlock("grid-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.STANDALONE, "entity-grid", Map.of(), Map.of("rows", "gone"), Map.of());
        stub(document, draftAtRevision(1L, List.of(broken)), null);

        assertThatThrownBy(() -> publisher(permitAll()).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentPublishingConflictException.class)
                .hasMessageContaining("cannot be published");
        verify(publishedRepository, never()).save(any());
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisher(permitAll()).execute(ORG, "missing", "en"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void anUntranslatedLocaleCannotBePublished() {
        Document document = document();
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "fr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisher(permitAll()).execute(ORG, ID, "fr"))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
    }

    @Test
    void publishingRequiresAPublisherRoleAndFallsBackToTheEditors() {
        Document document = document();
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false,
                new DocumentRoles(List.of(), List.of("editor"), List.of()), DocumentPorts.empty());
        stub(document, draftAtRevision(1L, List.of()), null);

        assertThatThrownBy(() -> publisher(holding("someone-else")).execute(ORG, ID, "en"))
                .isInstanceOf(DocumentAccessDeniedException.class);

        // No publisherRoles declared, so the editor role is what grants it.
        assertThat(publisher(holding("editor")).execute(ORG, ID, "en").status())
                .isEqualTo(DocumentStatus.PUBLISHED);
    }

    // region fixtures
    private PublishDocumentTranslation publisher(DocumentAccessPolicy policy) {
        ApplicationEventPublisher events = event -> publishedEvents.add(event);
        return new PublishDocumentTranslation(
                repository,
                draftRepository,
                publishedRepository,
                new DocumentReferentialIntegrityChecker(),
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy),
                events);
    }

    private void stub(Document document, DocumentDraft draft, PublishedDocument existingSnapshot) {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                .thenReturn(Optional.of(draft));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                .thenReturn(Optional.ofNullable(existingSnapshot));
        when(publishedRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Document document() {
        return new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
    }

    private static DocumentDraft draftAtRevision(long revision, List<DocumentBlock> blocks) {
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(blocks), null);
        // replaceBlocks bumps from 1, so reach the wanted revision by replaying edits — the same way
        // the draft would actually have got there.
        for (long r = 1; r < revision; r++) {
            draft.replaceBlocks(blocks);
        }
        return draft;
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentAccessPolicy permitAll() {
        return new DocumentAccessPolicy() {
        };
    }

    private static DocumentAccessPolicy holding(String... roles) {
        List<String> held = List.of(roles);
        return new DocumentAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return requiredRoles.stream().anyMatch(held::contains);
            }
        };
    }
    // endregion
}
