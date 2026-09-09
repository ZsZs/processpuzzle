package com.processpuzzle.document.adapter.inbound;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.processpuzzle.document.domain.AttributeVisibility;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentOutputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.model.DocumentSummary;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.model.DocumentTranslationSummary;
import com.processpuzzle.document.model.PageOfDocumentSummary;
import com.processpuzzle.document.model.PublishedContent;
import com.processpuzzle.document.usecase.DocumentTranslationView;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.document.usecase.ImportOutcome;
import com.processpuzzle.document.usecase.Severity;
import com.processpuzzle.shared.model.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapper is dumb by design, so what is worth asserting is not "field x reached field x" for its
 * own sake but the handful of places where it decides something: the string/UUID id bridge, the
 * null-vs-empty distinction on translation blocks, and the enum crossings.
 */
class DocumentMapperTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private final DocumentMapper mapper = new DocumentMapper();

    // ── model -> domain ─────────────────────────────────────────

    @Test
    void toDomainTakesIdentityFromTheCallerRatherThanThePayload() {
        DocumentInput input = input().id(UUID.randomUUID()).author("Ada");

        Document document = mapper.toDomain(ORG, ID, input, "grace");

        assertThat(document.getOrgKey()).isEqualTo(ORG);
        assertThat(document.getId()).isEqualTo(ID);
        assertThat(document.getCreatedBy()).isEqualTo("grace");
        assertThat(document.getAuthor()).isEqualTo("Ada");
        assertThat(document.getSlug()).isEqualTo("getting-started");
        assertThat(document.getTitle()).isEqualTo("Getting started");
        assertThat(document.getSubject()).isEqualTo("Onboarding");
        assertThat(document.getDescription()).isEqualTo("How to start");
        assertThat(document.getSourceLocale()).isEqualTo("en");
        assertThat(document.isPublic()).isTrue();
        assertThat(document.getRoles().readerRoles()).containsExactly("reader");
        assertThat(document.getPorts().inputPorts()).extracting(DocumentInputPort::name).containsExactly("customer");
    }

    @Test
    void anOmittedAuthorFallsBackToThePrincipal() {
        Document document = mapper.toDomain(ORG, ID, input().author(null).isPublic(null), "grace");

        assertThat(document.getAuthor()).isEqualTo("grace");
        assertThat(document.isPublic()).isFalse();
    }

    @Test
    void absentPortAndRoleListsBecomeEmptyOnes() {
        DocumentInput bare = new DocumentInput().slug("bare").title("Bare").sourceLocale("en")
                .inputPorts(null).outputPorts(null).readerRoles(null).editorRoles(null).publisherRoles(null);

        assertThat(mapper.toPorts(bare)).isEqualTo(DocumentPorts.empty());
        assertThat(mapper.toRoles(bare)).isEqualTo(DocumentRoles.unrestricted());
    }

    @Test
    void thePropertiesInputMapsPortsAndRolesTheSameWay() {
        DocumentPropertiesInput properties = new DocumentPropertiesInput()
                .slug("getting-started").title("Getting started").sourceLocale("en")
                .inputPorts(List.of(modelInputPort())).outputPorts(List.of(modelOutputPort()))
                .readerRoles(List.of("reader")).editorRoles(List.of("editor")).publisherRoles(List.of("publisher"));

        assertThat(mapper.toPorts(properties).inputPorts()).extracting(DocumentInputPort::name)
                .containsExactly("customer");
        assertThat(mapper.toPorts(properties).outputPorts()).extracting(DocumentOutputPort::name)
                .containsExactly("selection");
        assertThat(mapper.toRoles(properties).publisherRoles()).containsExactly("publisher");
    }

    @Test
    void omittedBlocksAreNullSoTheCallerCanTellThemFromABlankPage() {
        // The distinction addDocumentTranslation depends on: null means "copy the source locale",
        // an explicit empty array means "start blank".
        assertThat(mapper.toContentOrNull(new DocumentTranslationInput().locale("de"))).isNull();
        assertThat(mapper.toContentOrNull(new DocumentTranslationInput().locale("de").blocks(List.of())))
                .isEqualTo(DocumentContent.empty());
    }

    @Test
    void toContentOrNullKeepsTheIdEachBlockCarries() {
        DocumentTranslationInput input = new DocumentTranslationInput().locale("de")
                .blocks(List.of(blockInput("intro"), blockInput("outro")));

        assertThat(mapper.toContentOrNull(input).blocks())
                .extracting(DocumentBlock::id).containsExactly("intro", "outro");
    }

    @Test
    void toBlockCrossesTheEnumsAndToleratesAnAbsentPlacement() {
        ObjectNode content = JsonNodeFactory.instance.objectNode().put("type", "paragraph");
        DocumentBlockInput widget = new DocumentBlockInput()
                .kind(com.processpuzzle.document.model.BlockKind.WIDGET)
                .placement(com.processpuzzle.document.model.WidgetPlacement.REFERENCED)
                .editable(true)
                .content(content)
                .type("entity-grid")
                .props(Map.of("childIds", List.of("x")))
                .inputBindings(Map.of("rows", "customer"))
                .outputBindings(Map.of("selected", "selection"));

        DocumentBlock block = mapper.toBlock("grid-1", widget);

        assertThat(block.id()).isEqualTo("grid-1");
        assertThat(block.kind()).isEqualTo(BlockKind.WIDGET);
        assertThat(block.placement()).isEqualTo(WidgetPlacement.REFERENCED);
        assertThat(block.editable()).isTrue();
        assertThat(block.content()).isEqualTo(content);
        assertThat(block.type()).isEqualTo("entity-grid");
        assertThat(block.inputBindings()).containsEntry("rows", "customer");
        assertThat(block.outputBindings()).containsEntry("selected", "selection");

        DocumentBlockInput text = new DocumentBlockInput()
                .kind(com.processpuzzle.document.model.BlockKind.TEXT).placement(null);
        assertThat(mapper.toBlock("intro", text).placement()).isNull();
    }

    @Test
    void anAbsentAttributeVisibilityStaysAbsentAndAnAbsentModeDefaultsToAll() {
        DocumentInput noVisibility = new DocumentInput().slug("s").title("t").sourceLocale("en")
                .inputPorts(List.of(modelInputPort().attributeVisibility(null)))
                .outputPorts(List.of(modelOutputPort().attributeVisibility(null)));
        assertThat(mapper.toPorts(noVisibility).inputPorts().get(0).attributeVisibility()).isNull();
        assertThat(mapper.toPorts(noVisibility).outputPorts().get(0).attributeVisibility()).isNull();

        com.processpuzzle.document.model.AttributeVisibility modeless =
                new com.processpuzzle.document.model.AttributeVisibility().mode(null).attributes(List.of("name"));
        DocumentInput defaulted = new DocumentInput().slug("s").title("t").sourceLocale("en")
                .inputPorts(List.of(modelInputPort().attributeVisibility(modeless)));

        AttributeVisibility visibility = mapper.toPorts(defaulted).inputPorts().get(0).attributeVisibility();
        assertThat(visibility.mode()).isEqualTo(AttributeVisibility.Mode.ALL);
        assertThat(visibility.attributes()).containsExactly("name");
    }

    // ── domain -> model ─────────────────────────────────────────

    @Test
    void toModelBridgesTheStringIdOntoAUuidAndTheInstantsOntoUtcOffsets() {
        Document document = document();
        Instant when = Instant.parse("2026-01-02T03:04:05Z");
        document.markFirstPublication(when);

        com.processpuzzle.document.model.Document model =
                mapper.toModel(document, draftView(), List.of(draftView()));

        assertThat(model.getId()).isEqualTo(UUID.fromString(ID));
        assertThat(model.getOrgKey()).isEqualTo(ORG);
        assertThat(model.getPublishedAt()).isEqualTo(when.atOffset(ZoneOffset.UTC));
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
        assertThat(model.getTranslation()).isNotNull();
        assertThat(model.getTranslations()).hasSize(1);
        assertThat(model.getSlug()).isEqualTo("getting-started");
        assertThat(model.getInputPorts()).extracting(
                com.processpuzzle.document.model.DocumentInputPort::getName).containsExactly("customer");
    }

    @Test
    void aDocumentWithNoSelectedTranslationMapsToANullOne() {
        assertThat(mapper.toModel(document(), null, List.of()).getTranslation()).isNull();
    }

    @Test
    void aDocumentWithoutAnIdYetMapsToANullUuidRatherThanThrowing() {
        Document unsaved = new Document(ORG, null, "draft", "Draft", "en", "ada");

        assertThat(mapper.toModel(unsaved, null, List.of()).getId()).isNull();
        assertThat(mapper.toInput(unsaved, List.of()).getId()).isNull();
    }

    @Test
    void theSummaryCarriesEveryPropertyTheFullDocumentDoesExceptTheSelectedTranslation() {
        DocumentSummary summary = mapper.toSummaryModel(document(), List.of(draftView()));

        assertThat(summary.getId()).isEqualTo(UUID.fromString(ID));
        assertThat(summary.getTitle()).isEqualTo("Getting started");
        assertThat(summary.getEditorRoles()).containsExactly("editor");
        assertThat(summary.getTranslations()).extracting(DocumentTranslationSummary::getLocale).containsExactly("en");
    }

    @Test
    void aPageMapsEachRowWithItsOwnTranslationStatesAndKeepsThePagingFields() {
        PageOfDocumentSummary page = mapper.toModel(
                new PageImpl<>(List.of(document()), PageRequest.of(1, 5), 11),
                Map.of(ID, List.of(draftView())));

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getContent()).singleElement()
                .satisfies(summary -> assertThat(summary.getTranslations()).hasSize(1));
    }

    @Test
    void aDocumentMissingFromTheStatesMapGetsAnEmptyTranslationListRatherThanNull() {
        PageOfDocumentSummary page = mapper.toModel(
                new PageImpl<>(List.of(document()), PageRequest.of(0, 20), 1), Map.of());

        assertThat(page.getContent().get(0).getTranslations()).isEmpty();
    }

    @Test
    void theStateOnlyProjectionMapsToAnEmptyBlockList() {
        // stateOnly drops content on purpose; the mapper must not turn that into a null blocks array.
        DocumentTranslationView stateOnly = DocumentTranslationView.stateOnly(draft(), null, 1L);

        assertThat(mapper.toTranslationModel(stateOnly).getBlocks()).isEmpty();
    }

    @Test
    void aPublishedTranslationCarriesItsRevisionsAndStatus() {
        Instant when = Instant.parse("2026-01-02T03:04:05Z");
        PublishedDocument snapshot = new PublishedDocument(ORG, ID, "en",
                DocumentContent.of(List.of(text("intro"))), 1L, when, "ada");

        com.processpuzzle.document.model.DocumentTranslation model = mapper.toTranslationModel(
                DocumentTranslationView.ofPublished(snapshot, draft(), 1L));

        assertThat(model.getLocale()).isEqualTo("en");
        assertThat(model.getStatus()).isEqualTo(com.processpuzzle.document.model.DocumentStatus.PUBLISHED);
        assertThat(model.getRevision()).isEqualTo(1L);
        assertThat(model.getPublishedRevision()).isEqualTo(1L);
        assertThat(model.getPublishedAt()).isEqualTo(when.atOffset(ZoneOffset.UTC));
        assertThat(model.getBlocks()).extracting(
                com.processpuzzle.document.model.DocumentBlock::getId).containsExactly("intro");
    }

    @Test
    void theTranslationSummaryReportsTheBlockCountWithoutTheBlocks() {
        DocumentTranslationSummary summary = mapper.toTranslationSummaryModel(
                DocumentTranslationView.stateOnly(draft(), null, 1L));

        assertThat(summary.getBlockCount()).isEqualTo(1);
        assertThat(summary.getStatus()).isEqualTo(com.processpuzzle.document.model.DocumentStatus.DRAFT);
        assertThat(summary.getOutOfDate()).isFalse();
        assertThat(summary.getPublishedAt()).isNull();
    }

    @Test
    void thePublicViewExposesOnlyWhatTheContractDeclares() {
        PublishedContent model = mapper.toPublishedContentModel(
                document(), draftView(), true, List.of("en", "de"));

        assertThat(model.getSlug()).isEqualTo("getting-started");
        assertThat(model.getTitle()).isEqualTo("Getting started");
        assertThat(model.getSubject()).isEqualTo("Onboarding");
        assertThat(model.getDescription()).isEqualTo("How to start");
        assertThat(model.getAuthor()).isEqualTo("Ada");
        assertThat(model.getLocale()).isEqualTo("en");
        assertThat(model.getIsFallback()).isTrue();
        assertThat(model.getAvailableLocales()).containsExactly("en", "de");
        assertThat(model.getBlocks()).extracting(
                com.processpuzzle.document.model.DocumentBlock::getId).containsExactly("intro");
        assertThat(model.getInputPorts()).hasSize(1);
        assertThat(model.getOutputPorts()).hasSize(1);
    }

    @Test
    void aPublicViewOfAStateOnlyProjectionStillHasABlockList() {
        PublishedContent model = mapper.toPublishedContentModel(
                document(), DocumentTranslationView.stateOnly(draft(), null, 1L), false, List.of("en"));

        assertThat(model.getBlocks()).isEmpty();
    }

    @Test
    void exportRoundTripsThePropertiesAndEveryDraftsBlocks() {
        DocumentInput input = mapper.toInput(document(), List.of(draft()));

        assertThat(input.getId()).isEqualTo(UUID.fromString(ID));
        assertThat(input.getSlug()).isEqualTo("getting-started");
        assertThat(input.getIsPublic()).isTrue();
        assertThat(input.getReaderRoles()).containsExactly("reader");
        assertThat(input.getTranslations()).singleElement().satisfies(translation -> {
            assertThat(translation.getLocale()).isEqualTo("en");
            assertThat(translation.getBlocks()).extracting(DocumentBlockInput::getId).containsExactly("intro");
        });
    }

    @Test
    void bothBlockShapesCarryTheSameFieldsIncludingTheId() {
        // A round trip that dropped the id would break widgetEmbed and props.childIds references.
        DocumentBlock block = new DocumentBlock("grid-1", BlockKind.WIDGET, false, null,
                WidgetPlacement.REFERENCED, "entity-grid", Map.of("rows", 10),
                Map.of("rows", "customer"), Map.of("selected", "selection"));

        com.processpuzzle.document.model.DocumentBlock model = mapper.toModel(block);
        DocumentBlockInput modelInput = mapper.toModelInput(block);

        assertThat(model.getId()).isEqualTo("grid-1");
        assertThat(model.getKind()).isEqualTo(com.processpuzzle.document.model.BlockKind.WIDGET);
        assertThat(model.getPlacement()).isEqualTo(com.processpuzzle.document.model.WidgetPlacement.REFERENCED);
        assertThat(model.getEditable()).isFalse();
        assertThat(model.getContent()).isNull();
        assertThat(model.getProps()).containsEntry("rows", 10);
        assertThat(modelInput.getId()).isEqualTo("grid-1");
        assertThat(modelInput.getKind()).isEqualTo(com.processpuzzle.document.model.BlockKind.WIDGET);
        assertThat(modelInput.getPlacement()).isEqualTo(com.processpuzzle.document.model.WidgetPlacement.REFERENCED);
        assertThat(modelInput.getInputBindings()).containsEntry("rows", "customer");
        assertThat(modelInput.getOutputBindings()).containsEntry("selected", "selection");
    }

    @Test
    void aTextBlockWithoutAPlacementMapsToANullPlacement() {
        assertThat(mapper.toModel(text("intro")).getPlacement()).isNull();
        assertThat(mapper.toModelInput(text("intro")).getPlacement()).isNull();
    }

    @Test
    void portsKeepTheirVisibilityOnTheWayOutAndAnAbsentOneStaysAbsent() {
        com.processpuzzle.document.model.DocumentInputPort inputPort = mapper.toModel(inputPort());
        assertThat(inputPort.getName()).isEqualTo("customer");
        assertThat(inputPort.getType()).isEqualTo(com.processpuzzle.document.model.PortType.ENTITY_REF);
        assertThat(inputPort.getRequired()).isTrue();
        assertThat(inputPort.getDescription()).isEqualTo("The customer");
        assertThat(inputPort.getDefaultValue()).isEqualTo("42");
        assertThat(inputPort.getEntityType()).isEqualTo("Customer");
        assertThat(inputPort.getDefaultRsqlFilter()).isEqualTo("active==true");
        assertThat(inputPort.getAttributeVisibility().getMode())
                .isEqualTo(com.processpuzzle.document.model.AttributeVisibility.ModeEnum.INCLUDE);
        assertThat(inputPort.getAttributeVisibility().getAttributes()).containsExactly("name");

        com.processpuzzle.document.model.DocumentOutputPort outputPort = mapper.toModel(outputPort());
        assertThat(outputPort.getName()).isEqualTo("selection");
        assertThat(outputPort.getType()).isEqualTo(com.processpuzzle.document.model.PortType.ENTITY_COLLECTION);
        assertThat(outputPort.getDescription()).isEqualTo("What was picked");
        assertThat(outputPort.getEntityType()).isEqualTo("Customer");
        assertThat(outputPort.getAttributeVisibility()).isNull();
    }

    @Test
    void anImportOutcomeMapsToTheSharedImportResult() {
        ImportResult result = mapper.toModel(new ImportOutcome(2, 1, List.of("Entry 0 is missing 'slug'.")));

        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getErrors()).containsExactly("Entry 0 is missing 'slug'.");
    }

    @Test
    void aValidationResultBridgesBaseRulesSeverityOntoTheContractsOwn() {
        com.processpuzzle.document.model.ValidationResult result = mapper.toModel(false, List.of(
                new DocumentValidationProblem("/blocks/0", "document.validation.unknown-port",
                        "'gone' is not a declared input port.", Severity.ERROR, "en"),
                new DocumentValidationProblem("/blocks/1", "document.validation.orphaned-widget",
                        "Nothing points at it yet.", Severity.WARNING, "de")));

        assertThat(result.getValid()).isFalse();
        assertThat(result.getProblems()).hasSize(2);
        assertThat(result.getProblems().get(0).getPath()).isEqualTo("/blocks/0");
        assertThat(result.getProblems().get(0).getErrorId()).isEqualTo("document.validation.unknown-port");
        assertThat(result.getProblems().get(0).getErrorText()).isEqualTo("'gone' is not a declared input port.");
        assertThat(result.getProblems().get(0).getSeverity())
                .isEqualTo(com.processpuzzle.document.model.Severity.ERROR);
        assertThat(result.getProblems().get(0).getLocale()).isEqualTo("en");
        assertThat(result.getProblems().get(1).getSeverity())
                .isEqualTo(com.processpuzzle.document.model.Severity.WARNING);
    }

    // region fixtures
    private static Document document() {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", "Onboarding", "How to start", "Ada", "en",
                true,
                new DocumentRoles(List.of("reader"), List.of("editor"), List.of("publisher")),
                new DocumentPorts(List.of(inputPort()), List.of(outputPort())));
        return document;
    }

    private static DocumentDraft draft() {
        return new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);
    }

    private static DocumentTranslationView draftView() {
        return DocumentTranslationView.ofDraft(draft(), null, 1L);
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }

    private static DocumentInputPort inputPort() {
        return new DocumentInputPort("customer", PortType.ENTITY_REF, true, "The customer", "42", "Customer",
                new AttributeVisibility(AttributeVisibility.Mode.INCLUDE, List.of("name")), "active==true");
    }

    private static DocumentOutputPort outputPort() {
        return new DocumentOutputPort("selection", PortType.ENTITY_COLLECTION, "What was picked", "Customer", null);
    }

    private static com.processpuzzle.document.model.DocumentInputPort modelInputPort() {
        return new com.processpuzzle.document.model.DocumentInputPort()
                .name("customer")
                .type(com.processpuzzle.document.model.PortType.ENTITY_REF)
                .required(true)
                .entityType("Customer");
    }

    private static com.processpuzzle.document.model.DocumentOutputPort modelOutputPort() {
        return new com.processpuzzle.document.model.DocumentOutputPort()
                .name("selection")
                .type(com.processpuzzle.document.model.PortType.ENTITY_COLLECTION);
    }

    private static DocumentBlockInput blockInput(String id) {
        return new DocumentBlockInput().id(id).kind(com.processpuzzle.document.model.BlockKind.TEXT);
    }

    private static DocumentInput input() {
        return new DocumentInput()
                .slug("getting-started")
                .title("Getting started")
                .subject("Onboarding")
                .description("How to start")
                .sourceLocale("en")
                .isPublic(true)
                .readerRoles(List.of("reader"))
                .editorRoles(List.of("editor"))
                .publisherRoles(List.of("publisher"))
                .inputPorts(List.of(modelInputPort()))
                .outputPorts(List.of(modelOutputPort()));
    }
    // endregion
}
