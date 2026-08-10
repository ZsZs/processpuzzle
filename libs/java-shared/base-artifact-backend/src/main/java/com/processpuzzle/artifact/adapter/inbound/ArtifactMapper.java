package com.processpuzzle.artifact.adapter.inbound;

import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactInputPort;
import com.processpuzzle.artifact.domain.ArtifactOutputPort;
import com.processpuzzle.artifact.domain.AttributeVisibility;
import com.processpuzzle.artifact.domain.BlockKind;
import com.processpuzzle.artifact.domain.PortType;
import com.processpuzzle.artifact.domain.WidgetPlacement;
import com.processpuzzle.artifact.model.ArtifactBlockInput;
import com.processpuzzle.artifact.model.ArtifactInput;
import com.processpuzzle.artifact.model.ArtifactPropertiesInput;
import com.processpuzzle.artifact.model.ArtifactSummary;
import com.processpuzzle.artifact.model.PageOfArtifactSummary;
import com.processpuzzle.artifact.usecase.ArtifactValidationProblem;
import com.processpuzzle.artifact.usecase.ImportOutcome;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Field-for-field between the domain and the openapi-generator model classes generated from
 * base-artifact-api.yaml. Kept dumb on purpose — the interesting logic (referential integrity)
 * lives in {@code ArtifactReferentialIntegrityChecker}, not here.
 */
@Component
public class ArtifactMapper {

    /** {@code orgKey} comes from the path, not the body — same reasoning as RuleMapper. */
    public com.processpuzzle.artifact.domain.Artifact toDomain(String orgKey, ArtifactInput input) {
        return new com.processpuzzle.artifact.domain.Artifact(
                orgKey, input.getId(), input.getTitle(), input.getDescription(), toGraph(input));
    }

    public ArtifactGraph toGraph(ArtifactInput input) {
        List<ArtifactInputPort> inputPorts = input.getInputPorts() == null ? List.of()
                : input.getInputPorts().stream().map(this::toDomainInputPort).toList();
        List<ArtifactOutputPort> outputPorts = input.getOutputPorts() == null ? List.of()
                : input.getOutputPorts().stream().map(this::toDomainOutputPort).toList();
        List<ArtifactBlock> blocks = input.getBlocks() == null ? List.of()
                // Blocks arriving inside a whole-artifact ArtifactInput have no server-assigned
                // id yet on create; UpdateArtifact re-supplies the existing ids via the same
                // ArtifactBlockInput shape, so this mapper always trusts input.getId() here and
                // only the block-level use cases (Append/Replace) generate/pin an id explicitly.
                : input.getBlocks().stream().map(b -> toBlock(b.getId(), b)).toList();
        return new ArtifactGraph(inputPorts, outputPorts, blocks);
    }

    /**
     * The ports half of {@link #toGraph(ArtifactInput)}, merged onto the blocks already stored.
     * {@code ArtifactPropertiesInput} has no blocks field at all — that is the whole point of the
     * separate schema — so the caller supplies the current graph and only the ports are replaced.
     */
    public ArtifactGraph toGraph(ArtifactPropertiesInput input, ArtifactGraph current) {
        List<ArtifactInputPort> inputPorts = input.getInputPorts() == null ? List.of()
                : input.getInputPorts().stream().map(this::toDomainInputPort).toList();
        List<ArtifactOutputPort> outputPorts = input.getOutputPorts() == null ? List.of()
                : input.getOutputPorts().stream().map(this::toDomainOutputPort).toList();
        return current.withPorts(inputPorts, outputPorts);
    }

    public ArtifactBlock toBlock(String id, ArtifactBlockInput input) {
        return new ArtifactBlock(
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

    private ArtifactInputPort toDomainInputPort(com.processpuzzle.artifact.model.ArtifactInputPort model) {
        return new ArtifactInputPort(
                model.getName(),
                PortType.valueOf(model.getType().getValue()),
                Boolean.TRUE.equals(model.getRequired()),
                model.getDescription(),
                model.getDefaultValue(),
                model.getEntityType(),
                toDomainVisibility(model.getAttributeVisibility()),
                model.getDefaultRsqlFilter());
    }

    private ArtifactOutputPort toDomainOutputPort(com.processpuzzle.artifact.model.ArtifactOutputPort model) {
        return new ArtifactOutputPort(
                model.getName(),
                PortType.valueOf(model.getType().getValue()),
                model.getDescription(),
                model.getEntityType(),
                toDomainVisibility(model.getAttributeVisibility()));
    }

    private AttributeVisibility toDomainVisibility(com.processpuzzle.artifact.model.AttributeVisibility model) {
        if (model == null) {
            return null;
        }
        return new AttributeVisibility(
                model.getMode() == null ? AttributeVisibility.Mode.ALL
                        : AttributeVisibility.Mode.valueOf(model.getMode().getValue()),
                model.getAttributes());
    }

    // ── domain -> model ─────────────────────────────────────────

    public com.processpuzzle.artifact.model.Artifact toModel(com.processpuzzle.artifact.domain.Artifact artifact) {
        com.processpuzzle.artifact.model.Artifact model = new com.processpuzzle.artifact.model.Artifact();
        model.setId(artifact.getId());
        model.setOrgKey(artifact.getOrgKey());
        model.setTitle(artifact.getTitle());
        model.setDescription(artifact.getDescription());
        model.setVersion(artifact.getVersion());
        model.setInputPorts(artifact.getGraph().inputPorts().stream().map(this::toModel).toList());
        model.setOutputPorts(artifact.getGraph().outputPorts().stream().map(this::toModel).toList());
        model.setBlocks(artifact.getGraph().blocks().stream().map(this::toModel).toList());
        model.setCreatedAt(toOffsetDateTime(artifact.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(artifact.getUpdatedAt()));
        return model;
    }

    /** Round-trips a persisted artifact back into an {@code ArtifactInput} — used by {@code ExportArtifact}. */
    public ArtifactInput toInput(com.processpuzzle.artifact.domain.Artifact artifact) {
        ArtifactInput input = new ArtifactInput();
        input.setId(artifact.getId());
        input.setTitle(artifact.getTitle());
        input.setDescription(artifact.getDescription());
        input.setInputPorts(artifact.getGraph().inputPorts().stream().map(this::toModel).toList());
        input.setOutputPorts(artifact.getGraph().outputPorts().stream().map(this::toModel).toList());
        input.setBlocks(artifact.getGraph().blocks().stream().map(this::toModelInput).toList());
        return input;
    }

    public com.processpuzzle.artifact.model.ArtifactBlock toModel(ArtifactBlock block) {
        com.processpuzzle.artifact.model.ArtifactBlock model = new com.processpuzzle.artifact.model.ArtifactBlock();
        applyCommon(block, model::setId, model::setKind, model::setEditable, model::setContent,
                model::setPlacement, model::setType, model::setProps,
                model::setInputBindings, model::setOutputBindings);
        return model;
    }

    public ArtifactBlockInput toModelInput(ArtifactBlock block) {
        ArtifactBlockInput model = new ArtifactBlockInput();
        // Carries the id now that ArtifactBlockInput has one: this is the shape export writes and
        // import reads back, and widgetEmbed / props.childIds references would not survive a
        // round trip that dropped it.
        applyCommon(block, model::setId, model::setKind, model::setEditable, model::setContent,
                model::setPlacement, model::setType, model::setProps,
                model::setInputBindings, model::setOutputBindings);
        return model;
    }

    // Shared field assignment for ArtifactBlock -> {ArtifactBlock, ArtifactBlockInput} model classes,
    // which openapi-generator does not give a common supertype for.
    private void applyCommon(ArtifactBlock block,
            java.util.function.Consumer<String> setId,
            java.util.function.Consumer<com.processpuzzle.artifact.model.BlockKind> setKind,
            java.util.function.Consumer<Boolean> setEditable,
            java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> setContent,
            java.util.function.Consumer<com.processpuzzle.artifact.model.WidgetPlacement> setPlacement,
            java.util.function.Consumer<String> setType,
            java.util.function.Consumer<java.util.Map<String, Object>> setProps,
            java.util.function.Consumer<java.util.Map<String, String>> setInputBindings,
            java.util.function.Consumer<java.util.Map<String, String>> setOutputBindings) {
        setId.accept(block.id());
        setKind.accept(com.processpuzzle.artifact.model.BlockKind.fromValue(block.kind().name()));
        setEditable.accept(block.editable());
        setContent.accept(block.content());
        setPlacement.accept(block.placement() == null ? null
                : com.processpuzzle.artifact.model.WidgetPlacement.fromValue(block.placement().name()));
        setType.accept(block.type());
        setProps.accept(block.props());
        setInputBindings.accept(block.inputBindings());
        setOutputBindings.accept(block.outputBindings());
    }

    public com.processpuzzle.artifact.model.ArtifactInputPort toModel(ArtifactInputPort port) {
        com.processpuzzle.artifact.model.ArtifactInputPort model =
                new com.processpuzzle.artifact.model.ArtifactInputPort();
        model.setName(port.name());
        model.setType(com.processpuzzle.artifact.model.PortType.fromValue(port.type().name()));
        model.setRequired(port.required());
        model.setDescription(port.description());
        model.setDefaultValue(port.defaultValue());
        model.setEntityType(port.entityType());
        model.setAttributeVisibility(toModel(port.attributeVisibility()));
        model.setDefaultRsqlFilter(port.defaultRsqlFilter());
        return model;
    }

    public com.processpuzzle.artifact.model.ArtifactOutputPort toModel(ArtifactOutputPort port) {
        com.processpuzzle.artifact.model.ArtifactOutputPort model =
                new com.processpuzzle.artifact.model.ArtifactOutputPort();
        model.setName(port.name());
        model.setType(com.processpuzzle.artifact.model.PortType.fromValue(port.type().name()));
        model.setDescription(port.description());
        model.setEntityType(port.entityType());
        model.setAttributeVisibility(toModel(port.attributeVisibility()));
        return model;
    }

    private com.processpuzzle.artifact.model.AttributeVisibility toModel(AttributeVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        com.processpuzzle.artifact.model.AttributeVisibility model =
                new com.processpuzzle.artifact.model.AttributeVisibility();
        model.setMode(com.processpuzzle.artifact.model.AttributeVisibility.ModeEnum.fromValue(visibility.mode().name()));
        model.setAttributes(visibility.attributes());
        return model;
    }

    public ArtifactSummary toSummaryModel(com.processpuzzle.artifact.domain.Artifact artifact) {
        ArtifactSummary model = new ArtifactSummary();
        model.setId(artifact.getId());
        model.setOrgKey(artifact.getOrgKey());
        model.setTitle(artifact.getTitle());
        model.setVersion(artifact.getVersion());
        model.setBlockCount(artifact.getGraph().blocks().size());
        model.setUpdatedAt(toOffsetDateTime(artifact.getUpdatedAt()));
        return model;
    }

    public PageOfArtifactSummary toModel(Page<com.processpuzzle.artifact.domain.Artifact> page) {
        List<ArtifactSummary> content = page.getContent().stream().map(this::toSummaryModel).toList();
        return new PageOfArtifactSummary()
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

    public com.processpuzzle.artifact.model.ValidationResult toModel(
            boolean valid, List<ArtifactValidationProblem> problems) {
        com.processpuzzle.artifact.model.ValidationResult model =
                new com.processpuzzle.artifact.model.ValidationResult();
        model.setValid(valid);
        model.setProblems(problems.stream().map(this::toModel).toList());
        return model;
    }

    private com.processpuzzle.artifact.model.ValidationProblem toModel(ArtifactValidationProblem problem) {
        com.processpuzzle.artifact.model.ValidationProblem model =
                new com.processpuzzle.artifact.model.ValidationProblem();
        model.setPath(problem.path());
        model.setErrorId(problem.errorId());
        model.setErrorText(problem.errorText());
        // ArtifactValidationProblem.severity() is base-rule's own enum (see that class for why);
        // this is the one place that has to bridge it into the generated model's own enum.
        model.setSeverity(com.processpuzzle.artifact.model.Severity.fromValue(problem.severity().name()));
        return model;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
