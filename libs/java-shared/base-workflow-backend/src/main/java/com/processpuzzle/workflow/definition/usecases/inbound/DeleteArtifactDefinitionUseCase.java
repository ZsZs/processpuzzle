package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Refuses to remove an artifact a workflow still lists, a task still names as an input or output, or
 * a role still claims in {@code responsibleFor}. Three guards rather than one because the three
 * holders are independent: a task's inputs are reachable from a workflow only through the task, and a
 * role's ownership is a reference no workflow mentions at all.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class DeleteArtifactDefinitionUseCase {

    private final ArtifactDefinitionRepository repository;
    private final CatalogReferenceScanner referenceScanner;

    public void delete(String orgKey, String id) {
        ArtifactDefinition artifact = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No artifact definition with id '%s'".formatted(id)));

        List<String> workflows = referenceScanner.workflowsUsingArtifact(orgKey, id);
        if (!workflows.isEmpty()) {
            throw new ConflictException("Artifact '%s' is still used by workflows %s".formatted(id, workflows));
        }
        List<String> tasks = referenceScanner.tasksReferencingArtifact(orgKey, id);
        if (!tasks.isEmpty()) {
            throw new ConflictException("Artifact '%s' is still referenced by tasks %s as an input or output".formatted(id, tasks));
        }
        List<String> roles = referenceScanner.rolesResponsibleForArtifact(orgKey, id);
        if (!roles.isEmpty()) {
            throw new ConflictException("Artifact '%s' is still owned by roles %s via responsibleFor".formatted(id, roles));
        }
        repository.delete(artifact);
    }
}
