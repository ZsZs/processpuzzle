package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The other half of {@code WorkflowValidator}'s invariant: that one refuses a workflow
 * naming a role which does not exist, this one refuses to remove a role a workflow still names.
 * Both checks are needed, because between them lies the whole reason the catalog is shared.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class DeleteRoleDefinitionUseCase {

    private final RoleDefinitionRepository repository;
    private final CatalogReferenceScanner referenceScanner;

    public void delete(String orgKey, String id) {
        RoleDefinition role = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No role definition with id '%s'".formatted(id)));

        List<String> workflows = referenceScanner.workflowsUsingRole(orgKey, id);
        if (!workflows.isEmpty()) {
            throw new ConflictException("Role '%s' is still used by workflows %s".formatted(id, workflows));
        }
        List<String> tasks = referenceScanner.tasksOfferingRole(orgKey, id);
        if (!tasks.isEmpty()) {
            throw new ConflictException("Role '%s' is still offered by tasks %s in performedByRoles".formatted(id, tasks));
        }
        repository.delete(role);
    }
}
