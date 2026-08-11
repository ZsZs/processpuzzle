package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.AttributeVisibility;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentOutputPort;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PortType;
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
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Field-for-field between the domain and the openapi-generator model classes generated from
 * base-document-api.yaml. Kept dumb on purpose — the interesting logic lives in
 * {@code DocumentReferentialIntegrityChecker} and {@code DocumentTranslationAssembler}, not here.
 *
 * <p>Two shapes it deliberately does not know about: which of a draft and a published snapshot it
 * was handed (it maps a {@link DocumentTranslationView}, which has already resolved that), and how
 * a translation's status was computed. That is what keeps a publish-state bug out of the mapper.
 *
 * <p>The generated {@code id} is a {@link UUID} while the domain stores the canonical string — the
 * column stays a string so the composite key and a future document-id mapping in a
 * non-relational store both work — so this class is the one place that bridges the two.
 */
@Component
public class DocumentMapper {

    // ── model -> domain ─────────────────────────────────────────

    /**
     * {@code orgKey} comes from the path, not the body — same reasoning as RuleMapper. So does
     * {@code id}, which the caller has already generated: minting identity is a decision, not a
     * mapping, and {@code createdBy} likewise comes from the principal rather than the payload.
     */
    public com.processpuzzle.document.domain.Document toDomain(String orgKey, String id, DocumentInput input, String createdBy) {
        com.processpuzzle.document.domain.Document document = new com.processpuzzle.document.domain.Document(
                orgKey, id, input.getSlug(), input.getTitle(), input.getSourceLocale(), createdBy);
        document.replaceProperties(
                input.getSlug(),
                input.getTitle(),
                input.getSubject(),
                input.getDescription(),
                input.getAuthor() == null ? createdBy : input.getAuthor(),
                input.getSourceLocale(),
                Boolean.TRUE.equals(input.getIsPublic()),
                toRoles(input.getReaderRoles(), input.getEditorRoles(), input.getPublisherRoles()),
                toPorts(input.getInputPorts(), input.getOutputPorts()));
        return document;
    }

    public DocumentPorts toPorts(DocumentInput input) {
        return toPorts(input.getInputPorts(), input.getOutputPorts());
    }

    public DocumentPorts toPorts(DocumentPropertiesInput input) {
        return toPorts(input.getInputPorts(), input.getOutputPorts());
    }

    public DocumentRoles toRoles(DocumentInput input) {
        return toRoles(input.getReaderRoles(), input.getEditorRoles(), input.getPublisherRoles());
    }

    public DocumentRoles toRoles(DocumentPropertiesInput input) {
        return toRoles(input.getReaderRoles(), input.getEditorRoles(), input.getPublisherRoles());
    }

    /**
     * A translation's blocks, or {@code null} when the request carried none.
     *
     * <p>Null and empty mean different things here and the distinction is load-bearing: omitting
     * {@code blocks} on {@code addDocumentTranslation} asks for the source locale's blocks to be
     * copied, while an explicit empty array asks for a blank page. That is why this returns null
     * rather than {@link DocumentContent#empty()} — the caller has to be able to tell.
     */
    public DocumentContent toContentOrNull(DocumentTranslationInput input) {
        if (input.getBlocks() == null) {
            return null;
        }
        return DocumentContent.of(input.getBlocks().stream().map(block -> toBlock(block.getId(), block)).toList());
    }

    public DocumentBlock toBlock(String id, DocumentBlockInput input) {
        return new DocumentBlock(
                id,
                BlockKind.valueOf(input.getKind().getValue()),
                input.getEditable(),
                input.getContent(),
                input.getPlacement() == null ? null : WidgetPlacement.valueOf(input.getPlacement().getValue()),
                input.getType(),
                input.getProps(),
                input.getInputBindings(),
                input.getOutputBindings());
    }

    private DocumentPorts toPorts(List<com.processpuzzle.document.model.DocumentInputPort> inputPorts,
                                  List<com.processpuzzle.document.model.DocumentOutputPort> outputPorts) {
        return new DocumentPorts(
                inputPorts == null ? List.of() : inputPorts.stream().map(this::toDomainInputPort).toList(),
                outputPorts == null ? List.of() : outputPorts.stream().map(this::toDomainOutputPort).toList());
    }

    private DocumentRoles toRoles(List<String> readerRoles, List<String> editorRoles, List<String> publisherRoles) {
        return new DocumentRoles(readerRoles, editorRoles, publisherRoles);
    }

    private DocumentInputPort toDomainInputPort(com.processpuzzle.document.model.DocumentInputPort model) {
        return new DocumentInputPort(
                model.getName(),
                PortType.valueOf(model.getType().getValue()),
                Boolean.TRUE.equals(model.getRequired()),
                model.getDescription(),
                model.getDefaultValue(),
                model.getEntityType(),
                toDomainVisibility(model.getAttributeVisibility()),
                model.getDefaultRsqlFilter());
    }

    private DocumentOutputPort toDomainOutputPort(com.processpuzzle.document.model.DocumentOutputPort model) {
        return new DocumentOutputPort(
                model.getName(),
                PortType.valueOf(model.getType().getValue()),
                model.getDescription(),
                model.getEntityType(),
                toDomainVisibility(model.getAttributeVisibility()));
    }

    private AttributeVisibility toDomainVisibility(com.processpuzzle.document.model.AttributeVisibility model) {
        if (model == null) {
            return null;
        }
        return new AttributeVisibility(
                model.getMode() == null ? AttributeVisibility.Mode.ALL
                        : AttributeVisibility.Mode.valueOf(model.getMode().getValue()),
                model.getAttributes());
    }

    // ── domain -> model ─────────────────────────────────────────

    public com.processpuzzle.document.model.Document toModel(com.processpuzzle.document.domain.Document document,
                                                             DocumentTranslationView selected,
                                                             List<DocumentTranslationView> states) {
        com.processpuzzle.document.model.Document model = new com.processpuzzle.document.model.Document();
        applyProperties(document, model::setSlug, model::setTitle, model::setSubject, model::setDescription,
                model::setAuthor, model::setSourceLocale, model::setIsPublic, model::setReaderRoles,
                model::setEditorRoles, model::setPublisherRoles, model::setInputPorts, model::setOutputPorts);
        model.setId(toUuid(document.getId()));
        model.setOrgKey(document.getOrgKey());
        model.setTranslation(selected == null ? null : toTranslationModel(selected));
        model.setTranslations(states.stream().map(this::toTranslationSummaryModel).toList());
        model.setLockVersion(document.getLockVersion());
        model.setCreatedBy(document.getCreatedBy());
        model.setCreatedAt(toOffsetDateTime(document.getCreatedAt()));
        model.setPublishedAt(toOffsetDateTime(document.getPublishedAt()));
        model.setUpdatedAt(toOffsetDateTime(document.getUpdatedAt()));
        return model;
    }

    public DocumentSummary toSummaryModel(com.processpuzzle.document.domain.Document document,
                                          List<DocumentTranslationView> states) {
        DocumentSummary model = new DocumentSummary();
        applyProperties(document, model::setSlug, model::setTitle, model::setSubject, model::setDescription,
                model::setAuthor, model::setSourceLocale, model::setIsPublic, model::setReaderRoles,
                model::setEditorRoles, model::setPublisherRoles, model::setInputPorts, model::setOutputPorts);
        model.setId(toUuid(document.getId()));
        model.setOrgKey(document.getOrgKey());
        model.setTranslations(states.stream().map(this::toTranslationSummaryModel).toList());
        model.setLockVersion(document.getLockVersion());
        model.setCreatedBy(document.getCreatedBy());
        model.setCreatedAt(toOffsetDateTime(document.getCreatedAt()));
        model.setPublishedAt(toOffsetDateTime(document.getPublishedAt()));
        model.setUpdatedAt(toOffsetDateTime(document.getUpdatedAt()));
        return model;
    }

    public PageOfDocumentSummary toModel(Page<com.processpuzzle.document.domain.Document> page,
                                         Map<String, List<DocumentTranslationView>> statesByDocumentId) {
        List<DocumentSummary> content = page.getContent().stream()
                .map(document -> toSummaryModel(document, statesByDocumentId.getOrDefault(document.getId(), List.of())))
                .toList();
        return new PageOfDocumentSummary()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    public com.processpuzzle.document.model.DocumentTranslation toTranslationModel(DocumentTranslationView view) {
        com.processpuzzle.document.model.DocumentTranslation model =
                new com.processpuzzle.document.model.DocumentTranslation();
        model.setLocale(view.locale());
        model.setBlocks(view.content() == null ? List.of()
                : view.content().blocks().stream().map(this::toModel).toList());
        model.setStatus(com.processpuzzle.document.model.DocumentStatus.fromValue(view.status().name()));
        model.setRevision(view.revision());
        model.setPublishedRevision(view.publishedRevision());
        model.setBasedOnRevision(view.basedOnRevision());
        model.setOutOfDate(view.outOfDate());
        model.setPublishedAt(toOffsetDateTime(view.publishedAt()));
        model.setUpdatedAt(toOffsetDateTime(view.updatedAt()));
        return model;
    }

    public DocumentTranslationSummary toTranslationSummaryModel(DocumentTranslationView view) {
        DocumentTranslationSummary model = new DocumentTranslationSummary();
        model.setLocale(view.locale());
        model.setStatus(com.processpuzzle.document.model.DocumentStatus.fromValue(view.status().name()));
        model.setRevision(view.revision());
        model.setPublishedRevision(view.publishedRevision());
        model.setOutOfDate(view.outOfDate());
        model.setBlockCount(view.blockCount());
        model.setPublishedAt(toOffsetDateTime(view.publishedAt()));
        model.setUpdatedAt(toOffsetDateTime(view.updatedAt()));
        return model;
    }

    /**
     * The anonymous view. Every field it sets is one the contract's own schema declares, and that
     * schema has no draft, roles, audit or revision fields at all — so this method cannot widen the
     * public surface even if {@code Document} grows.
     */
    public PublishedContent toPublishedContentModel(com.processpuzzle.document.domain.Document document,
                                                    DocumentTranslationView served,
                                                    boolean isFallback,
                                                    List<String> availableLocales) {
        PublishedContent model = new PublishedContent();
        model.setSlug(document.getSlug());
        model.setTitle(document.getTitle());
        model.setSubject(document.getSubject());
        model.setDescription(document.getDescription());
        model.setAuthor(document.getAuthor());
        model.setPublishedAt(toOffsetDateTime(served.publishedAt()));
        model.setLocale(served.locale());
        model.setIsFallback(isFallback);
        model.setAvailableLocales(availableLocales);
        model.setBlocks(served.content() == null ? List.of()
                : served.content().blocks().stream().map(this::toModel).toList());
        model.setInputPorts(document.getPorts().inputPorts().stream().map(this::toModel).toList());
        model.setOutputPorts(document.getPorts().outputPorts().stream().map(this::toModel).toList());
        return model;
    }

    /**
     * Round-trips a persisted document back into a {@code DocumentInput} — used by
     * {@code ExportDocument}. Drafts rather than snapshots, because an export is a copy of the
     * source of truth; publication state is deliberately not exportable (see the contract).
     */
    public DocumentInput toInput(com.processpuzzle.document.domain.Document document, List<DocumentDraft> drafts) {
        DocumentInput input = new DocumentInput();
        applyProperties(document, input::setSlug, input::setTitle, input::setSubject, input::setDescription,
                input::setAuthor, input::setSourceLocale, input::setIsPublic, input::setReaderRoles,
                input::setEditorRoles, input::setPublisherRoles, input::setInputPorts, input::setOutputPorts);
        input.setId(toUuid(document.getId()));
        input.setTranslations(drafts.stream().map(this::toTranslationInput).toList());
        return input;
    }

    public DocumentTranslationInput toTranslationInput(DocumentDraft draft) {
        DocumentTranslationInput model = new DocumentTranslationInput();
        model.setLocale(draft.getLocale());
        model.setBlocks(draft.getBlocks().stream().map(this::toModelInput).toList());
        return model;
    }

    public com.processpuzzle.document.model.DocumentBlock toModel(DocumentBlock block) {
        com.processpuzzle.document.model.DocumentBlock model = new com.processpuzzle.document.model.DocumentBlock();
        applyCommon(block, model::setId, model::setKind, model::setEditable, model::setContent,
                model::setPlacement, model::setType, model::setProps,
                model::setInputBindings, model::setOutputBindings);
        return model;
    }

    public DocumentBlockInput toModelInput(DocumentBlock block) {
        DocumentBlockInput model = new DocumentBlockInput();
        // Carries the id now that DocumentBlockInput has one: this is the shape export writes and
        // import reads back, and widgetEmbed / props.childIds references would not survive a
        // round trip that dropped it.
        applyCommon(block, model::setId, model::setKind, model::setEditable, model::setContent,
                model::setPlacement, model::setType, model::setProps,
                model::setInputBindings, model::setOutputBindings);
        return model;
    }

    public com.processpuzzle.document.model.DocumentInputPort toModel(DocumentInputPort port) {
        com.processpuzzle.document.model.DocumentInputPort model =
                new com.processpuzzle.document.model.DocumentInputPort();
        model.setName(port.name());
        model.setType(com.processpuzzle.document.model.PortType.fromValue(port.type().name()));
        model.setRequired(port.required());
        model.setDescription(port.description());
        model.setDefaultValue(port.defaultValue());
        model.setEntityType(port.entityType());
        model.setAttributeVisibility(toModel(port.attributeVisibility()));
        model.setDefaultRsqlFilter(port.defaultRsqlFilter());
        return model;
    }

    public com.processpuzzle.document.model.DocumentOutputPort toModel(DocumentOutputPort port) {
        com.processpuzzle.document.model.DocumentOutputPort model =
                new com.processpuzzle.document.model.DocumentOutputPort();
        model.setName(port.name());
        model.setType(com.processpuzzle.document.model.PortType.fromValue(port.type().name()));
        model.setDescription(port.description());
        model.setEntityType(port.entityType());
        model.setAttributeVisibility(toModel(port.attributeVisibility()));
        return model;
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    public com.processpuzzle.document.model.ValidationResult toModel(
            boolean valid, List<DocumentValidationProblem> problems) {
        com.processpuzzle.document.model.ValidationResult model =
                new com.processpuzzle.document.model.ValidationResult();
        model.setValid(valid);
        model.setProblems(problems.stream().map(this::toModel).toList());
        return model;
    }

    private com.processpuzzle.document.model.AttributeVisibility toModel(AttributeVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        com.processpuzzle.document.model.AttributeVisibility model =
                new com.processpuzzle.document.model.AttributeVisibility();
        model.setMode(com.processpuzzle.document.model.AttributeVisibility.ModeEnum.fromValue(visibility.mode().name()));
        model.setAttributes(visibility.attributes());
        return model;
    }

    private com.processpuzzle.document.model.ValidationProblem toModel(DocumentValidationProblem problem) {
        com.processpuzzle.document.model.ValidationProblem model =
                new com.processpuzzle.document.model.ValidationProblem();
        model.setPath(problem.path());
        model.setErrorId(problem.errorId());
        model.setErrorText(problem.errorText());
        // DocumentValidationProblem.severity() is base-rule's own enum (see that class for why);
        // this is the one place that has to bridge it into the generated model's own enum.
        model.setSeverity(com.processpuzzle.document.model.Severity.fromValue(problem.severity().name()));
        model.setLocale(problem.locale());
        return model;
    }

    // Shared field assignment for the invariant properties, which appear on Document,
    // DocumentSummary and DocumentInput alike — openapi-generator flattens the shared allOf
    // branch into each class instead of giving them a common supertype.
    private void applyProperties(com.processpuzzle.document.domain.Document document,
            java.util.function.Consumer<String> setSlug,
            java.util.function.Consumer<String> setTitle,
            java.util.function.Consumer<String> setSubject,
            java.util.function.Consumer<String> setDescription,
            java.util.function.Consumer<String> setAuthor,
            java.util.function.Consumer<String> setSourceLocale,
            java.util.function.Consumer<Boolean> setIsPublic,
            java.util.function.Consumer<List<String>> setReaderRoles,
            java.util.function.Consumer<List<String>> setEditorRoles,
            java.util.function.Consumer<List<String>> setPublisherRoles,
            java.util.function.Consumer<List<com.processpuzzle.document.model.DocumentInputPort>> setInputPorts,
            java.util.function.Consumer<List<com.processpuzzle.document.model.DocumentOutputPort>> setOutputPorts) {
        setSlug.accept(document.getSlug());
        setTitle.accept(document.getTitle());
        setSubject.accept(document.getSubject());
        setDescription.accept(document.getDescription());
        setAuthor.accept(document.getAuthor());
        setSourceLocale.accept(document.getSourceLocale());
        setIsPublic.accept(document.isPublic());
        setReaderRoles.accept(document.getRoles().readerRoles());
        setEditorRoles.accept(document.getRoles().editorRoles());
        setPublisherRoles.accept(document.getRoles().publisherRoles());
        setInputPorts.accept(document.getPorts().inputPorts().stream().map(this::toModel).toList());
        setOutputPorts.accept(document.getPorts().outputPorts().stream().map(this::toModel).toList());
    }

    // Shared field assignment for DocumentBlock -> {DocumentBlock, DocumentBlockInput} model classes,
    // which openapi-generator does not give a common supertype for.
    private void applyCommon(DocumentBlock block,
            java.util.function.Consumer<String> setId,
            java.util.function.Consumer<com.processpuzzle.document.model.BlockKind> setKind,
            java.util.function.Consumer<Boolean> setEditable,
            java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> setContent,
            java.util.function.Consumer<com.processpuzzle.document.model.WidgetPlacement> setPlacement,
            java.util.function.Consumer<String> setType,
            java.util.function.Consumer<java.util.Map<String, Object>> setProps,
            java.util.function.Consumer<java.util.Map<String, String>> setInputBindings,
            java.util.function.Consumer<java.util.Map<String, String>> setOutputBindings) {
        setId.accept(block.id());
        setKind.accept(com.processpuzzle.document.model.BlockKind.fromValue(block.kind().name()));
        setEditable.accept(block.editable());
        setContent.accept(block.content());
        setPlacement.accept(block.placement() == null ? null
                : com.processpuzzle.document.model.WidgetPlacement.fromValue(block.placement().name()));
        setType.accept(block.type());
        setProps.accept(block.props());
        setInputBindings.accept(block.inputBindings());
        setOutputBindings.accept(block.outputBindings());
    }

    private static UUID toUuid(String id) {
        return id == null ? null : UUID.fromString(id);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
