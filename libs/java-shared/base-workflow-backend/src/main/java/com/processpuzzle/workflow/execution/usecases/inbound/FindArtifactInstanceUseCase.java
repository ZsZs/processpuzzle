package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FindArtifactInstanceUseCase {

    private final ArtifactInstanceRepository repository;

    public FindArtifactInstanceUseCase(ArtifactInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ArtifactInstance find(String orgKey, UUID processInstanceId, String artifactDefinitionId) {
        return repository.findByOrgKeyAndProcessInstanceIdAndArtifactDefinitionId(orgKey, processInstanceId, artifactDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No artifact '%s' in process instance '%s'".formatted(artifactDefinitionId, processInstanceId)));
    }
}
