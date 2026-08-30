package com.processpuzzle.workflow.definition.adapters.inbound;

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
 * <p>Organization-scoped, not process-scoped: a artifact belongs to the tenant's catalog and may be
 * referenced by any number of process definitions.
 */
@RestController
public class ArtifactDefinitionsEndpoint implements ArtifactDefinitionsApi {

    private final CreateArtifactDefinitionUseCase createArtifactDefinition;
    private final ReplaceArtifactDefinitionUseCase replaceArtifactDefinition;
    private final DeleteArtifactDefinitionUseCase deleteArtifactDefinition;
    private final FindArtifactDefinitionUseCase findArtifactDefinition;
    private final FindAllArtifactDefinitionsUseCase findAllArtifactDefinitions;
    private final WorkflowDefinitionMapper mapper;

    public ArtifactDefinitionsEndpoint(CreateArtifactDefinitionUseCase createArtifactDefinition,
                                    ReplaceArtifactDefinitionUseCase replaceArtifactDefinition,
                                    DeleteArtifactDefinitionUseCase deleteArtifactDefinition,
                                    FindArtifactDefinitionUseCase findArtifactDefinition,
                                    FindAllArtifactDefinitionsUseCase findAllArtifactDefinitions,
                                    WorkflowDefinitionMapper mapper) {
        this.createArtifactDefinition = createArtifactDefinition;
        this.replaceArtifactDefinition = replaceArtifactDefinition;
        this.deleteArtifactDefinition = deleteArtifactDefinition;
        this.findArtifactDefinition = findArtifactDefinition;
        this.findAllArtifactDefinitions = findAllArtifactDefinitions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ArtifactDefinition>> listArtifactDefinitions(String orgKey, String where, String order) {
        var artifacts = findAllArtifactDefinitions.findAll(orgKey, where, order);
        return ResponseEntity.ok(artifacts.stream().map(mapper::toArtifactModel).toList());
    }

    @Override
    public ResponseEntity<ArtifactDefinition> createArtifactDefinition(String orgKey, ArtifactDefinitionInput input) {
        var created = createArtifactDefinition.create(orgKey, mapper.toArtifactDomain(input));
        return new ResponseEntity<>(mapper.toArtifactModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ArtifactDefinition> getArtifactDefinition(String orgKey, String artifactId) {
        return ResponseEntity.ok(mapper.toArtifactModel(findArtifactDefinition.findByOrgKeyAndId(orgKey, artifactId)));
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
