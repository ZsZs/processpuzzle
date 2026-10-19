package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateDocumentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private UpdateDocument updateDocument;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        updateDocument = new UpdateDocument(
                repository,
                draftRepository,
                publishedRepository,
                new DocumentReferentialIntegrityChecker(),
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.permitAll(),
                TestFacts.ignored());
    }

    @Test
    void replacesTheInvariantPropertiesWholesale() {
        Document existing = existing();
        stubFound(existing, draft("en", List.of()));

        DocumentDetails details = updateDocument.execute(ORG, ID, new DocumentCommand(
                new DocumentProperties("getting-started", "Renamed", "Onboarding", "Updated", "Grace", "en",
                        true, new DocumentRoles(List.of("reader"), List.of(), List.of()), input().properties().ports()),
                List.of()));

        assertThat(details.document().getTitle()).isEqualTo("Renamed");
        assertThat(details.document().getSubject()).isEqualTo("Onboarding");
        assertThat(details.document().getDescription()).isEqualTo("Updated");
        assertThat(details.document().getAuthor()).isEqualTo("Grace");
        assertThat(details.document().isPublic()).isTrue();
        assertThat(details.document().getRoles().readerRoles()).containsExactly("reader");
        verify(repository).save(existing);
    }

    @Test
    void replacesTheDraftBlocksOfEveryLocaleTheRequestNames() {
        Document existing = existing();
        DocumentDraft english = draft("en", List.of(text("old")));
        stubFound(existing, english);

        updateDocument.execute(ORG, ID, input(List.of(
                translation("en", text("new")))));

        assertThat(english.getBlocks()).extracting(DocumentBlock::id).containsExactly("new");
        assertThat(english.getRevision()).isEqualTo(2L);
        verify(draftRepository).save(english);
    }

    @Test
    void aLocaleTheRequestOmitsIsLeftAloneRatherThanDeleted() {
        // Removing a language is RemoveDocumentTranslation's job, so an omission cannot do it silently.
        Document existing = existing();
        DocumentDraft german = draft("de", List.of(text("einleitung")));
        stubFound(existing, draft("en", List.of()), german);

        updateDocument.execute(ORG, ID, input(List.of(translation("en"))));

        assertThat(german.getBlocks()).extracting(DocumentBlock::id).containsExactly("einleitung");
        verify(draftRepository, never()).save(german);
    }

    @Test
    void aNamedTranslationThatCarriesNoBlocksAtAllIsNotAReplacement() {
        Document existing = existing();
        DocumentDraft english = draft("en", List.of(text("old")));
        stubFound(existing, english);

        updateDocument.execute(ORG, ID, input(List.of(new DocumentTranslationCommand("en", null))));

        assertThat(english.getBlocks()).extracting(DocumentBlock::id).containsExactly("old");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void namingALocaleThatHasNoTranslationIsAnErrorRatherThanAnImplicitCreate() {
        // Creating one has to decide which source revision it is based on — AddDocumentTranslation's job.
        stubFound(existing(), draft("en", List.of()));

        assertThatThrownBy(() -> updateDocument.execute(ORG, ID, input(List.of(translation("fr", text("intro"))))))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void everyLocaleIsValidatedAgainstTheNewPortsBeforeAnythingIsWritten() {
        // The German draft is untouched by this request, but the port it binds to is being removed.
        Document existing = existing();
        DocumentDraft german = draft("de", List.of(widgetBoundTo("grid-1", "customer")));
        stubFound(existing, draft("en", List.of()), german);

        assertThatThrownBy(() -> updateDocument.execute(ORG, ID, input("getting-started", DocumentPorts.empty(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document");
        verify(repository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void theCandidateContentIsWhatGetsValidatedNotTheContentAlreadyStored() {
        Document existing = existing();
        DocumentDraft english = draft("en", List.of());
        stubFound(existing, english);

        DocumentBlock broken = widgetBoundTo(null, "gone");

        assertThatThrownBy(() -> updateDocument.execute(ORG, ID, input(List.of(translation("en", broken)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(english.getBlocks()).isEmpty();
    }

    @Test
    void replacingDraftContentDoesNotAuthorizeBreakingPublishedBindings() {
        Document document = existing();
        DocumentDraft english = draft("en", List.of(widgetBoundTo("grid", "customer")));
        stubFound(document, english);
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(
                new PublishedDocument(ORG, ID, "en", english.getContent(), 1L, Instant.now(), "ada")));

        assertThatThrownBy(() -> updateDocument.execute(ORG, ID, input("getting-started", DocumentPorts.empty(),
                List.of(translation("en")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown-port");
        verify(repository, never()).save(any());
        verify(draftRepository, never()).save(any());
        assertThat(english.getBlocks()).hasSize(1);
        assertThat(document.getPorts().inputPorts()).hasSize(1);
    }

    @Test
    void portsAndDraftBindingsCanBeRemovedTogetherWhenNothingPublishedReferencesThem() {
        Document document = existing();
        DocumentDraft english = draft("en", List.of(widgetBoundTo("grid", "customer")));
        stubFound(document, english);

        updateDocument.execute(ORG, ID, input("getting-started", DocumentPorts.empty(), List.of(translation("en"))));

        assertThat(document.getPorts().inputPorts()).isEmpty();
        assertThat(english.getBlocks()).isEmpty();
    }

    @Test
    void aTakenSlugIsAConflictButKeepingItsOwnIsNot() {
        Document existing = existing();
        stubFound(existing, draft("en", List.of()));
        when(repository.existsByOrgKeyAndSlug(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> updateDocument.execute(ORG, ID, input("taken", input().properties().ports(), List.of())))
                .isInstanceOf(DocumentSlugAlreadyExistsException.class);
        assertThat(updateDocument.execute(ORG, ID, input()).document().getSlug()).isEqualTo("getting-started");
    }

    @Test
    void renamingToASlugNobodyElseUsesGoesThrough() {
        Document existing = existing();
        stubFound(existing, draft("en", List.of()));

        assertThat(updateDocument.execute(ORG, ID, input("renamed", input().properties().ports(), List.of())).document().getSlug())
                .isEqualTo("renamed");
    }

    @Test
    void anOmittedTranslationsArrayLeavesEveryLocaleAlone() {
        Document existing = existing();
        DocumentDraft english = draft("en", List.of(text("old")));
        stubFound(existing, english);

        updateDocument.execute(ORG, ID, input(null));

        assertThat(english.getBlocks()).extracting(DocumentBlock::id).containsExactly("old");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateDocument.execute(ORG, "missing", input()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private void stubFound(Document document, DocumentDraft... drafts) {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(drafts));
        for (DocumentDraft draft : drafts) {
            when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                    .thenReturn(Optional.of(draft));
        }
    }

    private static Document existing() {
        Document document = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        document.replaceProperties("getting-started", "Original", null, null, "ada", "en", false, null,
                new DocumentPorts(List.of(customerPort()), List.of()));
        return document;
    }

    private static DocumentDraft draft(String locale, List<DocumentBlock> blocks) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(blocks), null);
    }

    private static DocumentInputPort customerPort() {
        return new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null);
    }

    private static DocumentCommand input() {
        return input(List.of());
    }

    private static DocumentCommand input(List<DocumentTranslationCommand> translations) {
        return input("getting-started", new DocumentPorts(List.of(customerPort()), List.of()), translations);
    }

    private static DocumentCommand input(String slug, DocumentPorts ports, List<DocumentTranslationCommand> translations) {
        return new DocumentCommand(new DocumentProperties(slug, "Original", null, null, null, "en",
                false, null, ports), translations);
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentTranslationCommand translation(String locale, DocumentBlock... blocks) {
        return new DocumentTranslationCommand(locale, DocumentContent.of(List.of(blocks)));
    }

    private static DocumentBlock widgetBoundTo(String id, String portName) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE,
                "entity-grid", Map.of(), Map.of("rows", portName), Map.of());
    }
    // endregion
}
