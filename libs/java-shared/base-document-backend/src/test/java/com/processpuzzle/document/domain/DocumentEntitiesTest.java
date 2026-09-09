package com.processpuzzle.document.domain;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The domain's own rules — the ones that hold regardless of which use case is calling.
 */
class DocumentEntitiesTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    // ── Document ────────────────────────────────────────────────

    @Test
    void aNewDocumentStartsUnrestrictedAndWithoutPorts() {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");

        assertThat(document.getRoles()).isEqualTo(DocumentRoles.unrestricted());
        assertThat(document.getPorts()).isEqualTo(DocumentPorts.empty());
        assertThat(document.getAuthor()).isEqualTo("ada");
        assertThat(document.getLockVersion()).isNull();
        assertThat(document.getPublishedAt()).isNull();
    }

    @Test
    void clearingTheRolesOrPortsMeansUnrestrictedAndEmptyRatherThanNull() {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");

        document.replaceProperties("renamed", "Renamed", null, null, null, "de", true, null, null);

        assertThat(document.getSlug()).isEqualTo("renamed");
        assertThat(document.getSourceLocale()).isEqualTo("de");
        assertThat(document.isPublic()).isTrue();
        assertThat(document.getRoles()).isEqualTo(DocumentRoles.unrestricted());
        assertThat(document.getPorts()).isEqualTo(DocumentPorts.empty());
    }

    @Test
    void thePublicationDateRecordsTheFirstLocaleToGoLiveAndNeverMovesAgain() {
        // publishedAt keeps meaning "when this document went live" rather than "when it was last
        // touched" — updatedAt and each translation's own publishedAt answer that.
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        Instant first = Instant.parse("2026-01-02T03:04:05Z");

        document.markFirstPublication(first);
        document.markFirstPublication(Instant.parse("2026-06-07T08:09:10Z"));

        assertThat(document.getPublishedAt()).isEqualTo(first);
    }

    // ── DocumentDraft ──────────────────────────────────────────

    @Test
    void aDraftStartsAtRevisionOneAndBumpsOnEveryContentWrite() {
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);

        assertThat(draft.getRevision()).isEqualTo(1L);

        draft.replaceBlocks(List.of(text("intro"), text("outro")));

        assertThat(draft.getRevision()).isEqualTo(2L);
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("intro", "outro");
    }

    @Test
    void aDraftCreatedWithoutContentIsEmptyRatherThanNull() {
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", null, null);

        assertThat(draft.getContent()).isEqualTo(DocumentContent.empty());
        assertThat(draft.getBlocks()).isEmpty();
        assertThat(draft.getOrgKey()).isEqualTo(ORG);
        assertThat(draft.getDocumentId()).isEqualTo(ID);
        assertThat(draft.getLocale()).isEqualTo("en");
        assertThat(draft.getLockVersion()).isNull();
        // The timestamps are set by the JPA lifecycle callbacks, so an unpersisted draft has none.
        assertThat(draft.getCreatedAt()).isNull();
        assertThat(draft.getUpdatedAt()).isNull();
    }

    @Test
    void revertingTakesThePublishedRevisionBackRatherThanCountingForward() {
        // Discarding a draft returns to a state that was already published, so status has to derive
        // back to PUBLISHED — which it cannot if the counter moves.
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);
        draft.replaceBlocks(List.of(text("edited")));

        draft.revertTo(DocumentContent.of(List.of(text("published"))), 1L);

        assertThat(draft.getRevision()).isEqualTo(1L);
        assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("published");
    }

    @Test
    void revertingToNothingLeavesAnEmptyDraftRatherThanANullOne() {
        DocumentDraft draft = new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);

        draft.revertTo(null, 1L);

        assertThat(draft.getBlocks()).isEmpty();
    }

    @Test
    void staleMeansTheSourceHasMovedOnSinceThisTranslationWasMade() {
        DocumentDraft translation = new DocumentDraft(ORG, ID, "de", DocumentContent.empty(), 2L);

        assertThat(translation.isOutOfDate(3L)).isTrue();
        assertThat(translation.isOutOfDate(2L)).isFalse();
        assertThat(translation.isOutOfDate(1L)).isFalse();
        assertThat(translation.isOutOfDate(null)).isFalse();
    }

    @Test
    void aTranslationWithNoRecordedBaseIsNeverReportedStale() {
        // The source locale itself is the common case; guessing about an unknown base would cry wolf
        // on every import.
        DocumentDraft source = new DocumentDraft(ORG, ID, "en", DocumentContent.empty(), null);

        assertThat(source.isOutOfDate(99L)).isFalse();

        source.rebaseOn(1L);

        assertThat(source.getBasedOnRevision()).isEqualTo(1L);
        assertThat(source.isOutOfDate(99L)).isTrue();
    }

    // ── PublishedDocument ───────────────────────────────────────

    @Test
    void aSnapshotIsReplacedWholesaleRatherThanMerged() {
        PublishedDocument snapshot = new PublishedDocument(ORG, ID, "en",
                DocumentContent.of(List.of(text("old"))), 1L, Instant.parse("2026-01-02T03:04:05Z"), "ada");
        Instant later = Instant.parse("2026-06-07T08:09:10Z");

        snapshot.replaceSnapshot(DocumentContent.of(List.of(text("new"))), 5L, later, "grace");

        assertThat(snapshot.getBlocks()).extracting(DocumentBlock::id).containsExactly("new");
        assertThat(snapshot.getPublishedRevision()).isEqualTo(5L);
        assertThat(snapshot.getPublishedAt()).isEqualTo(later);
        assertThat(snapshot.getPublishedBy()).isEqualTo("grace");
        assertThat(snapshot.getOrgKey()).isEqualTo(ORG);
        assertThat(snapshot.getDocumentId()).isEqualTo(ID);
        assertThat(snapshot.getLocale()).isEqualTo("en");
    }

    @Test
    void aSnapshotWithoutContentIsEmptyRatherThanNull() {
        PublishedDocument snapshot = new PublishedDocument(ORG, ID, "en", null, 1L, Instant.now(), "ada");

        assertThat(snapshot.getContent()).isEqualTo(DocumentContent.empty());

        snapshot.replaceSnapshot(null, 2L, Instant.now(), "ada");

        assertThat(snapshot.getBlocks()).isEmpty();
    }

    // ── value types ─────────────────────────────────────────────

    @Test
    void statusIsDerivedFromTheTwoRevisionsRatherThanStored() {
        assertThat(DocumentStatus.derive(3L, null)).isEqualTo(DocumentStatus.DRAFT);
        assertThat(DocumentStatus.derive(3L, 3L)).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(DocumentStatus.derive(4L, 3L)).isEqualTo(DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES);
    }

    @Test
    void publisherRolesFallBackToTheEditorsWhenNoneAreDeclared() {
        // Defined once next to the data rather than re-derived at each call site.
        assertThat(new DocumentRoles(List.of(), List.of("editor"), List.of()).effectivePublisherRoles())
                .containsExactly("editor");
        assertThat(new DocumentRoles(List.of(), List.of("editor"), List.of("publisher")).effectivePublisherRoles())
                .containsExactly("publisher");
        assertThat(DocumentRoles.unrestricted().effectivePublisherRoles()).isEmpty();
    }

    @Test
    void absentRoleAndPortListsNormaliseToEmptyOnes() {
        assertThat(new DocumentRoles(null, null, null)).isEqualTo(DocumentRoles.unrestricted());
        assertThat(new DocumentPorts(null, null)).isEqualTo(DocumentPorts.empty());
        assertThat(new AttributeVisibility(null, null)).isEqualTo(AttributeVisibility.all());
    }

    @Test
    void portsKnowWhichNamesTheyDeclare() {
        DocumentPorts ports = new DocumentPorts(
                List.of(new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, null, null, null)),
                List.of(new DocumentOutputPort("selection", PortType.ENTITY_COLLECTION, null, null, null)));

        assertThat(ports.declaresInputPort("customer")).isTrue();
        assertThat(ports.declaresInputPort("selection")).isFalse();
        assertThat(ports.declaresOutputPort("selection")).isTrue();
        assertThat(ports.declaresOutputPort("customer")).isFalse();
    }

    @Test
    void contentCanFindABlockAndListItsWidgets() {
        DocumentContent content = DocumentContent.of(List.of(
                text("intro"), widget("grid-1", WidgetPlacement.REFERENCED), widget("chart-1", null)));

        assertThat(content.findBlock("grid-1")).isPresent();
        assertThat(content.findBlock("missing")).isEmpty();
        assertThat(content.widgetBlockIds()).containsExactly("grid-1", "chart-1");
        assertThat(content.withBlocks(List.of(text("only"))).blocks())
                .extracting(DocumentBlock::id).containsExactly("only");
        assertThat(DocumentContent.of(null)).isEqualTo(DocumentContent.empty());
    }

    @Test
    void onlyAReferencedWidgetIsReferenced() {
        assertThat(text("intro").isWidget()).isFalse();
        assertThat(text("intro").isReferenced()).isFalse();
        assertThat(widget("chart-1", WidgetPlacement.STANDALONE).isWidget()).isTrue();
        assertThat(widget("chart-1", WidgetPlacement.STANDALONE).isReferenced()).isFalse();
        assertThat(widget("grid-1", WidgetPlacement.REFERENCED).isReferenced()).isTrue();
    }

    @Test
    void aBlocksMapsAreNeverNullAndItsTiptapContentStaysOpaque() {
        DocumentBlock block = new DocumentBlock("intro", BlockKind.TEXT, true,
                JsonNodeFactory.instance.objectNode().put("type", "doc"), null, null, null, null, null);

        assertThat(block.props()).isEqualTo(Map.of());
        assertThat(block.inputBindings()).isEqualTo(Map.of());
        assertThat(block.outputBindings()).isEqualTo(Map.of());
        assertThat(block.content().get("type").asText()).isEqualTo("doc");
    }

    // region fixtures
    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentBlock widget(String id, WidgetPlacement placement) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, placement, "entity-grid", null, null, null);
    }
    // endregion
}
