package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.adapters.inbound.release.ReleaseStates;
import com.processpuzzle.workflow.definition.adapters.inbound.release.ArtifactReleaseParticipant;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.api.ArtifactDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllArtifactDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceArtifactDefinitionUseCase;
import com.processpuzzle.workflow.model.ArtifactDefinition;
import com.processpuzzle.workflow.model.ArtifactDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements the generated {@code ArtifactDefinitionsApi} (from the "Artifact Definitions" tag).
 *
 * <p>Organization-scoped, not workflow-scoped: a artifact belongs to the tenant's catalog and may be
 * referenced by any number of workflow definitions.
 */
@RestController
public class ArtifactDefinitionsEndpoint implements ArtifactDefinitionsApi {

    private final CreateArtifactDefinitionUseCase createArtifactDefinition;
    private final ReplaceArtifactDefinitionUseCase replaceArtifactDefinition;
    private final DeleteArtifactDefinitionUseCase deleteArtifactDefinition;
    private final FindArtifactDefinitionUseCase findArtifactDefinition;
    private final FindAllArtifactDefinitionsUseCase findAllArtifactDefinitions;
    private final WorkflowDefinitionMapper mapper;
    private final ArtifactReleaseParticipant releaseParticipant;

    public ArtifactDefinitionsEndpoint(CreateArtifactDefinitionUseCase createArtifactDefinition,
                                    ReplaceArtifactDefinitionUseCase replaceArtifactDefinition,
                                    DeleteArtifactDefinitionUseCase deleteArtifactDefinition,
                                    FindArtifactDefinitionUseCase findArtifactDefinition,
                                    FindAllArtifactDefinitionsUseCase findAllArtifactDefinitions,
                                    WorkflowDefinitionMapper mapper,
            ArtifactReleaseParticipant releaseParticipant) {
        this.createArtifactDefinition = createArtifactDefinition;
        this.replaceArtifactDefinition = replaceArtifactDefinition;
        this.deleteArtifactDefinition = deleteArtifactDefinition;
        this.findArtifactDefinition = findArtifactDefinition;
        this.findAllArtifactDefinitions = findAllArtifactDefinitions;
        this.mapper = mapper;
        this.releaseParticipant = releaseParticipant;
    }

    @Override
    public ResponseEntity<List<ArtifactDefinition>> listArtifactDefinitions(String orgKey, String where, String order, String revision) {
        Channel channel = Channel.ofRevision(revision);
        var artifacts = findAllArtifactDefinitions.findAll(orgKey, channel, where, order).stream().map(mapper::toArtifactModel).toList();
        return ResponseEntity.ok(ReleaseStates.stamp(channel, releaseParticipant, orgKey, artifacts,
                ArtifactDefinition::getId, ArtifactDefinition::setReleaseState));
    }

    @Override
    public ResponseEntity<ArtifactDefinition> createArtifactDefinition(String orgKey, ArtifactDefinitionInput input) {
        var created = createArtifactDefinition.create(orgKey, mapper.toArtifactDomain(input));
        return new ResponseEntity<>(mapper.toArtifactModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ArtifactDefinition> getArtifactDefinition(String orgKey, String artifactId, String revision) {
        Channel channel = Channel.ofRevision(revision);
        var artifact = mapper.toArtifactModel(findArtifactDefinition.findByOrgKeyAndId(orgKey, artifactId, channel));
        return ResponseEntity.ok(ReleaseStates.stampOne(channel, releaseParticipant, orgKey, artifact,
                ArtifactDefinition::getId, ArtifactDefinition::setReleaseState));
    }

    @Override
    public ResponseEntity<ArtifactDefinition> updateArtifactDefinition(String orgKey, String artifactId, ArtifactDefinitionInput input) {
        var updated = replaceArtifactDefinition.replace(orgKey, artifactId, mapper.toArtifactDomain(input));
        return ResponseEntity.ok(mapper.toArtifactModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteArtifactDefinition(String orgKey, String artifactId) {
        deleteArtifactDefinition.delete(orgKey, artifactId);
        return ResponseEntity.noContent().build();
    }
}
