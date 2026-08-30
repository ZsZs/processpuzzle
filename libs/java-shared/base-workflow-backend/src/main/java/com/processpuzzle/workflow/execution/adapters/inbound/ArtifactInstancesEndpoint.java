package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.ArtifactInstancesApi;
import com.processpuzzle.workflow.execution.usecases.inbound.FindArtifactInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListArtifactInstancesUseCase;
import com.processpuzzle.workflow.model.ArtifactInstance;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code ArtifactInstancesApi} (from the "Artifact Instances" tag). */
@RestController
public class ArtifactInstancesEndpoint implements ArtifactInstancesApi {

    private final ListArtifactInstancesUseCase listArtifactInstances;
    private final FindArtifactInstanceUseCase findArtifactInstance;
    private final WorkflowExecutionMapper mapper;

    public ArtifactInstancesEndpoint(ListArtifactInstancesUseCase listArtifactInstances,
                                         FindArtifactInstanceUseCase findArtifactInstance,
                                         WorkflowExecutionMapper mapper) {
        this.listArtifactInstances = listArtifactInstances;
        this.findArtifactInstance = findArtifactInstance;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<ArtifactInstance>> listArtifactInstances(String orgKey, String instanceId, String where, String order) {
        var artifacts = listArtifactInstances.findAll(orgKey, UUID.fromString(instanceId));
        return ResponseEntity.ok(artifacts.stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ArtifactInstance> getArtifactInstance(String orgKey, String instanceId, String artifactId) {
        var artifact = findArtifactInstance.find(orgKey, UUID.fromString(instanceId), artifactId);
        return ResponseEntity.ok(mapper.toModel(artifact));
    }
}
