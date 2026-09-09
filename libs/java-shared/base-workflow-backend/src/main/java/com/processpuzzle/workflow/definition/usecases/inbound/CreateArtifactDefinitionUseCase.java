package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a artifact to the organization's catalog. Nothing is checked against the workflows that may
 * later reference it: a artifact exists on its own, and it is
 * {@code WorkflowValidator} that refuses a workflow naming one that does not.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CreateArtifactDefinitionUseCase {

    private final ArtifactDefinitionRepository repository;

    public ArtifactDefinition create(String orgKey, ArtifactDefinition artifact) {
        artifact.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, artifact.getId())) {
            throw new ConflictException("Artifact definition '%s' already exists".formatted(artifact.getId()));
        }
        return repository.save(artifact);
    }
}
