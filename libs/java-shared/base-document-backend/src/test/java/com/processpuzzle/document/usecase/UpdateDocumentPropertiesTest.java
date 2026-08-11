package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
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
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateDocumentPropertiesTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private UpdateDocumentProperties updateDocumentProperties;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        updateDocumentProperties = new UpdateDocumentProperties(
                repository,
                draftRepository,
                new DocumentReferentialIntegrityChecker(),
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.permitAll(),
                new DocumentMapper());
    }

    @Test
    void updatesTheMetadataAndNeverWritesContent() {
        // The whole reason this use case exists: DocumentPropertiesInput has no content field, so a
        // Properties save cannot discard blocks maintained through the translation endpoints.
        Document existing = existing();
        DocumentDraft draft = draft(List.of(standaloneWidget("chart-1", Map.of())));
        stubFound(existing, draft);

        DocumentDetails details = updateDocumentProperties.execute(ORG, ID, input("Renamed"));

        assertThat(details.document().getTitle()).isEqualTo("Renamed");
        assertThat(details.document().getSubject()).isEqualTo("Onboarding");
        assertThat(details.document().getAuthor()).isEqualTo("Ada");
        verify(draftRepository, never()).save(any());
        assertThat(draft.getRevision()).isEqualTo(1L);
    }

    @Test
    void replacesThePortsRatherThanMergingThem() {
        Document existing = existing();
        stubFound(existing, draft(List.of()));

        DocumentDetails details = updateDocumentProperties.execute(ORG, ID, input("Renamed"));

        assertThat(details.document().getPorts().inputPorts())
                .extracting(DocumentInputPort::name).containsExactly("customer");
    }

    @Test
    void rejectsDeletingAPortThatAnUntouchedTranslationStillBindsTo() {
        // Ports are invariant, so deleting one here orphans bindings in every language at once —
        // which is why the check sweeps all translations rather than just the one on screen.
        Document existing = existing();
        DocumentDraft german = draft("de", List.of(widgetBoundTo("grid-1", "customer")));
        stubFound(existing, draft(List.of()), german);

        DocumentPropertiesInput noPorts = new DocumentPropertiesInput()
                .slug("getting-started").title("Renamed").sourceLocale("en").inputPorts(List.of());

        assertThatThrownBy(() -> updateDocumentProperties.execute(ORG, ID, noPorts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document properties");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsASlugAnotherDocumentAlreadyUses() {
        Document existing = existing();
        stubFound(existing, draft(List.of()));
        when(repository.existsByOrgKeyAndSlug(ORG, "taken")).thenReturn(true);

        DocumentPropertiesInput renamed = input("Renamed").slug("taken");

        assertThatThrownBy(() -> updateDocumentProperties.execute(ORG, ID, renamed))
                .isInstanceOf(DocumentSlugAlreadyExistsException.class);
    }

    @Test
    void keepingItsOwnSlugIsNotASlugCollision() {
        // Guards against the obvious off-by-one in the uniqueness check: saving a document without
        // renaming it must not trip over its own slug.
        Document existing = existing();
        stubFound(existing, draft(List.of()));
        when(repository.existsByOrgKeyAndSlug(anyString(), anyString())).thenReturn(true);

        DocumentDetails details = updateDocumentProperties.execute(ORG, ID, input("Renamed"));

        assertThat(details.document().getSlug()).isEqualTo("getting-started");
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateDocumentProperties.execute(ORG, "missing", input("Renamed")))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private void stubFound(Document document, DocumentDraft... drafts) {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(drafts));
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of());
        for (DocumentDraft draft : drafts) {
            when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, draft.getLocale()))
                    .thenReturn(Optional.of(draft));
        }
    }

    private static Document existing() {
        Document document = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        document.replaceProperties("getting-started", "Original", null, "Original description", "ada", "en", false,
                null, new DocumentPorts(List.of(customerPort()), List.of()));
        return document;
    }

    private static DocumentDraft draft(List<DocumentBlock> blocks) {
        return draft("en", blocks);
    }

    private static DocumentDraft draft(String locale, List<DocumentBlock> blocks) {
        return new DocumentDraft(ORG, ID, locale, DocumentContent.of(blocks), null);
    }

    private static DocumentInputPort customerPort() {
        return new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null);
    }

    private static DocumentPropertiesInput input(String title) {
        return new DocumentPropertiesInput()
                .slug("getting-started")
                .title(title)
                .subject("Onboarding")
                .description("Updated description")
                .author("Ada")
                .sourceLocale("en")
                .inputPorts(List.of(new com.processpuzzle.document.model.DocumentInputPort()
                        .name("customer")
                        .type(com.processpuzzle.document.model.PortType.ENTITY_REF)
                        .required(true)
                        .entityType("Customer")))
                .outputPorts(List.of());
    }

    private static DocumentBlock standaloneWidget(String id, Map<String, Object> props) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE,
                "entity-grid", props, Map.of(), Map.of());
    }

    private static DocumentBlock widgetBoundTo(String id, String portName) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE,
                "entity-grid", Map.of(), Map.of("rows", portName), Map.of());
    }
    // endregion
}
