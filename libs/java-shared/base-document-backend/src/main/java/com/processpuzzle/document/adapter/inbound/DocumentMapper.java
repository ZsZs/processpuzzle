package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentOutputPort;
import com.processpuzzle.document.domain.AttributeVisibility;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.model.DocumentSummary;
import com.processpuzzle.document.model.PageOfDocumentSummary;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.document.usecase.ImportOutcome;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Field-for-field between the domain and the openapi-generator model classes generated from
 * base-document-api.yaml. Kept dumb on purpose — the interesting logic (referential integrity)
 * lives in {@code DocumentReferentialIntegrityChecker}, not here.
 */
@Component
public class DocumentMapper {

    /** {@code orgKey} comes from the path, not the body — same reasoning as RuleMapper. */
    public com.processpuzzle.document.domain.Document toDomain(String orgKey, DocumentInput input) {
        return new com.processpuzzle.document.domain.Document(
                orgKey, input.getId(), input.getTitle(), input.getDescription(), toGraph(input));
    }

    public DocumentGraph toGraph(DocumentInput input) {
        List<DocumentInputPort> inputPorts = input.getInputPorts() == null ? List.of()
                : input.getInputPorts().stream().map(this::toDomainInputPort).toList();
        List<DocumentOutputPort> outputPorts = input.getOutputPorts() == null ? List.of()
                : input.getOutputPorts().stream().map(this::toDomainOutputPort).toList();
        List<DocumentBlock> blocks = input.getBlocks() == null ? List.of()
                // Blocks arriving inside a whole-document DocumentInput have no server-assigned
                // id yet on create; UpdateDocument re-supplies the existing ids via the same
                // DocumentBlockInput shape, so this mapper always trusts input.getId() here and
                // only the block-level use cases (Append/Replace) generate/pin an id explicitly.
                : input.getBlocks().stream().map(b -> toBlock(b.getId(), b)).toList();
        return new DocumentGraph(inputPorts, outputPorts, blocks);
    }

    /**
     * The ports half of {@link #toGraph(DocumentInput)}, merged onto the blocks already stored.
     * {@code DocumentPropertiesInput} has no blocks field at all — that is the whole point of the
     * separate schema — so the caller supplies the current graph and only the ports are replaced.
     */
    public DocumentGraph toGraph(DocumentPropertiesInput input, DocumentGraph current) {
        List<DocumentInputPort> inputPorts = input.getInputPorts() == null ? List.of()
                : input.getInputPorts().stream().map(this::toDomainInputPort).toList();
        List<DocumentOutputPort> outputPorts = input.getOutputPorts() == null ? List.of()
                : input.getOutputPorts().stream().map(this::toDomainOutputPort).toList();
        return current.withPorts(inputPorts, outputPorts);
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

    public com.processpuzzle.document.model.Document toModel(com.processpuzzle.document.domain.Document document) {
        com.processpuzzle.document.model.Document model = new com.processpuzzle.document.model.Document();
        model.setId(document.getId());
        model.setOrgKey(document.getOrgKey());
        model.setTitle(document.getTitle());
        model.setDescription(document.getDescription());
        model.setVersion(document.getVersion());
        model.setInputPorts(document.getGraph().inputPorts().stream().map(this::toModel).toList());
        model.setOutputPorts(document.getGraph().outputPorts().stream().map(this::toModel).toList());
        model.setBlocks(document.getGraph().blocks().stream().map(this::toModel).toList());
        model.setCreatedAt(toOffsetDateTime(document.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(document.getUpdatedAt()));
        return model;
    }

    /** Round-trips a persisted document back into an {@code DocumentInput} — used by {@code ExportDocument}. */
    public DocumentInput toInput(com.processpuzzle.document.domain.Document document) {
        DocumentInput input = new DocumentInput();
        input.setId(document.getId());
        input.setTitle(document.getTitle());
        input.setDescription(document.getDescription());
        input.setInputPorts(document.getGraph().inputPorts().stream().map(this::toModel).toList());
        input.setOutputPorts(document.getGraph().outputPorts().stream().map(this::toModel).toList());
        input.setBlocks(document.getGraph().blocks().stream().map(this::toModelInput).toList());
        return input;
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

    public DocumentSummary toSummaryModel(com.processpuzzle.document.domain.Document document) {
        DocumentSummary model = new DocumentSummary();
        model.setId(document.getId());
        model.setOrgKey(document.getOrgKey());
        model.setTitle(document.getTitle());
        model.setVersion(document.getVersion());
        model.setBlockCount(document.getGraph().blocks().size());
        model.setUpdatedAt(toOffsetDateTime(document.getUpdatedAt()));
        return model;
    }

    public PageOfDocumentSummary toModel(Page<com.processpuzzle.document.domain.Document> page) {
        List<DocumentSummary> content = page.getContent().stream().map(this::toSummaryModel).toList();
        return new PageOfDocumentSummary()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
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

    private com.processpuzzle.document.model.ValidationProblem toModel(DocumentValidationProblem problem) {
        com.processpuzzle.document.model.ValidationProblem model =
                new com.processpuzzle.document.model.ValidationProblem();
        model.setPath(problem.path());
        model.setErrorId(problem.errorId());
        model.setErrorText(problem.errorText());
        // DocumentValidationProblem.severity() is base-rule's own enum (see that class for why);
        // this is the one place that has to bridge it into the generated model's own enum.
        model.setSeverity(com.processpuzzle.document.model.Severity.fromValue(problem.severity().name()));
        return model;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
