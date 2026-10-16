package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentBlockNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentBlockReferencedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentDraftEditor;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.junit.jupiter.api.BeforeEach;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
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

/**
 * The four block-level use cases together, because they are four block-list transformations handed to
 * the same {@link DocumentDraftEditor} — and the load-authorize-validate-save sequence around them is
 * shared, so testing it once per use case would say the same thing four times.
 */
class DocumentBlockEditingTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;
    private DocumentDraft draft;
    private final List<PlatformEvent> facts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── append ──────────────────────────────────────────────────

    @Test
    void appendPutsTheNewBlockLastAndMintsItsId() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));

        DocumentBlock appended = new AppendDocumentBlock(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", textInput());

        assertThat(appended.id()).isNotBlank();
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("intro", appended.id());
        assertThat(draft.getRevision()).isEqualTo(2L);
    }

    // ── replace ─────────────────────────────────────────────────

    @Test
    void replaceKeepsThePositionAndTheId() {
        stubFound(DocumentRoles.unrestricted(), text("intro"), text("outro"));

        DocumentBlock replacement = new ReplaceDocumentBlock(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", "intro", textInput());

        assertThat(replacement.id()).isEqualTo("intro");
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("intro", "outro");
        assertThat(draft.getBlocks().get(0)).isEqualTo(replacement);
    }

    @Test
    void replacingABlockThatIsNotThereIsNotFound() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));

        assertThatThrownBy(() -> new ReplaceDocumentBlock(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", "missing", textInput()))
                .isInstanceOf(DocumentBlockNotFoundException.class);
        verify(draftRepository, never()).save(any());
    }

    // ── delete ──────────────────────────────────────────────────

    @Test
    void deleteRemovesJustThatBlock() {
        stubFound(DocumentRoles.unrestricted(), text("intro"), text("outro"));

        deleteBlock(TestPolicies.permitAll()).execute(ORG, ID, "en", "intro");

        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("outro");
    }

    @Test
    void deletingABlockThatIsNotThereIsNotFound() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));

        assertThatThrownBy(() -> deleteBlock(TestPolicies.permitAll()).execute(ORG, ID, "en", "missing"))
                .isInstanceOf(DocumentBlockNotFoundException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aBlockNamedInAnotherBlocksChildIdsCannotBeDeleted() {
        // The referenced-by check runs against the content as it stands: taking the block out first
        // would make it invisible to its own referrers and leave dangling references behind.
        stubFound(DocumentRoles.unrestricted(), referencedWidget("grid-1"), widgetWithChildIds("host", "grid-1"));

        assertThatThrownBy(() -> deleteBlock(TestPolicies.permitAll()).execute(ORG, ID, "en", "grid-1"))
                .isInstanceOf(DocumentBlockReferencedException.class)
                .hasMessageContaining("grid-1");
        assertThat(draft.getBlocks()).hasSize(2);
    }

    @Test
    void aBlockEmbeddedInAnotherBlocksTiptapContentCannotBeDeletedEither() {
        stubFound(DocumentRoles.unrestricted(), referencedWidget("grid-1"), textEmbedding("prose", "grid-1"));

        assertThatThrownBy(() -> deleteBlock(TestPolicies.permitAll()).execute(ORG, ID, "en", "grid-1"))
                .isInstanceOf(DocumentBlockReferencedException.class)
                .satisfies(thrown -> assertThat(((DocumentBlockReferencedException) thrown).getReferencingBlockIds())
                        .containsExactly("prose"));
    }

    // ── reorder ─────────────────────────────────────────────────

    @Test
    void reorderRewritesThePositionsAndAnswersWithTheNewOrder() {
        stubFound(DocumentRoles.unrestricted(), text("intro"), text("body"), text("outro"));

        List<DocumentBlock> reordered = new ReorderDocumentBlocks(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", List.of("outro", "intro", "body"));

        assertThat(reordered).extracting(DocumentBlock::id).containsExactly("outro", "intro", "body");
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("outro", "intro", "body");
    }

    @Test
    void anIdSetThatIsNotThisTranslationsIsRejectedRatherThanPartiallyApplied() {
        // No position field exists to fall back on, so an omitted or added id has to be refused.
        stubFound(DocumentRoles.unrestricted(), text("intro"), text("outro"));

        assertThatThrownBy(() -> new ReorderDocumentBlocks(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", List.of("intro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact permutation");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aRepeatedIdIsRejectedEvenThoughTheIdSetMatches() {
        stubFound(DocumentRoles.unrestricted(), text("intro"), text("outro"));

        assertThatThrownBy(() -> new ReorderDocumentBlocks(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", List.of("intro", "outro", "outro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact permutation");
    }

    // ── the shared sequence ─────────────────────────────────────

    @Test
    void theCandidateContentIsValidatedBeforeItIsStored() {
        // The whole reason the sequence lives in one place: validating what is about to be stored
        // rather than what was already there is the step easiest to leave out.
        stubFound(DocumentRoles.unrestricted());

        DocumentBlock boundToNothing = new DocumentBlock(null, BlockKind.WIDGET, null, null, null,
                "entity-grid", null, Map.of("rows", "gone"), null);

        assertThatThrownBy(() -> new AppendDocumentBlock(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", boundToNothing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document content");
        assertThat(draft.getBlocks()).isEmpty();
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aWidgetBoundToADeclaredPortIsAccepted() {
        stubFound(DocumentRoles.unrestricted());

        DocumentBlock bound = new DocumentBlock(null, BlockKind.WIDGET, null, null, null,
                "entity-grid", null, Map.of("rows", "customer"), null);

        DocumentBlock appended = new AppendDocumentBlock(editor(TestPolicies.permitAll()))
                .execute(ORG, ID, "en", bound);

        assertThat(draft.getBlocks()).containsExactly(appended);
    }

    @Test
    void editingBlocksRequiresAnEditorRole() {
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()), text("intro"));

        assertThatThrownBy(() -> deleteBlock(TestPolicies.holding("reader")).execute(ORG, ID, "en", "intro"))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void editingAnUnknownDocumentOrLocaleIsNotFound() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteBlock(TestPolicies.permitAll()).execute(ORG, "missing", "en", "intro"))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> deleteBlock(TestPolicies.permitAll()).execute(ORG, ID, "fr", "intro"))
                .isInstanceOf(DocumentTranslationNotFoundException.class);
    }

    // ── platform facts ──────────────────────────────────────────

    @Test
    void theFirstEditAfterAPublishIsAStateChangeOfTheDocument() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en")).thenReturn(Optional.of(
                new PublishedDocument(ORG, ID, "en", draft.getContent(), 1L, Instant.now(), "ada")));

        new AppendDocumentBlock(editor(TestPolicies.permitAll())).execute(ORG, ID, "en", textInput());
        new AppendDocumentBlock(editor(TestPolicies.permitAll())).execute(ORG, ID, "en", textInput());

        // The second edit leaves it PUBLISHED_WITH_DRAFT_CHANGES, so it is no fact.
        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.action()).isEqualTo(PlatformEventAction.STATE_CHANGED);
            assertThat(fact.subjectType()).isEqualTo("document");
            assertThat(fact.subjectId()).isEqualTo(ID);
            assertThat(fact.state()).isEqualTo("PUBLISHED_WITH_DRAFT_CHANGES");
        });
    }

    @Test
    void editingAnUnpublishedDraftChangesNoState() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));

        new AppendDocumentBlock(editor(TestPolicies.permitAll())).execute(ORG, ID, "en", textInput());

        assertThat(facts).isEmpty();
    }

    @Test
    void editingAnotherTranslationIsNotTheDocumentsState() {
        stubFound(DocumentRoles.unrestricted(), text("intro"));
        DocumentDraft german = new DocumentDraft(ORG, ID, "de", DocumentContent.of(List.of(text("intro"))), 1L);
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "de")).thenReturn(Optional.of(german));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "de")).thenReturn(Optional.of(
                new PublishedDocument(ORG, ID, "de", german.getContent(), 1L, Instant.now(), "ada")));

        new AppendDocumentBlock(editor(TestPolicies.permitAll())).execute(ORG, ID, "de", textInput());

        assertThat(facts).isEmpty();
    }

    // region fixtures
    private DocumentDraftEditor editor(DocumentAccessPolicy policy) {
        return new DocumentDraftEditor(repository, draftRepository, publishedRepository,
                new DocumentReferentialIntegrityChecker(), TestGuards.with(policy), TestFacts.recordingInto(facts));
    }

    private DeleteDocumentBlock deleteBlock(DocumentAccessPolicy policy) {
        return new DeleteDocumentBlock(editor(policy), new DocumentReferentialIntegrityChecker());
    }

    private void stubFound(DocumentRoles roles, DocumentBlock... blocks) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                new DocumentPorts(List.of(customerPort()), List.of()));
        draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(blocks)), null);
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en")).thenReturn(Optional.of(draft));
    }

    private static DocumentInputPort customerPort() {
        return new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null);
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentBlock textInput() {
        return text("ignored-client-id");
    }

    private static DocumentBlock referencedWidget(String id) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.REFERENCED, "entity-grid",
                Map.of(), Map.of(), Map.of());
    }

    private static DocumentBlock widgetWithChildIds(String id, String... childIds) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE, "layout",
                Map.of("childIds", List.of(childIds)), Map.of(), Map.of());
    }

    private static DocumentBlock textEmbedding(String id, String blockId) {
        ObjectNode embed = JsonNodeFactory.instance.objectNode();
        embed.put("type", "widgetEmbed").putObject("attrs").put("blockId", blockId);
        ObjectNode doc = JsonNodeFactory.instance.objectNode();
        doc.put("type", "doc").putArray("content").add(embed);
        return new DocumentBlock(id, BlockKind.TEXT, true, doc, null, null, null, null, null);
    }
    // endregion
}
