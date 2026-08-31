package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace of a artifact definition. Field-by-field onto the loaded row rather than saving the
 * incoming one, so that {@code orgKey}, {@code id} and the audit columns survive — the same
 * convention {@link ReplaceToolDefinitionUseCase} follows.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceArtifactDefinitionUseCase {

    private final ArtifactDefinitionRepository repository;

    public ArtifactDefinition replace(String orgKey, String id, ArtifactDefinition desiredState) {
        ArtifactDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No artifact definition with id '%s'".formatted(id)));

        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new ConflictException(
                    "Artifact definition '%s' was modified concurrently — reload and retry".formatted(id));
        }

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setArtifactType(desiredState.getArtifactType());
        existing.setArtifactTypeId(desiredState.getArtifactTypeId());
        existing.setStateMachineId(desiredState.getStateMachineId());
        return repository.save(existing);
    }
}
