package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a role to an existing process definition. Mutates through the {@link ProcessDefinition}
 * aggregate root rather than a dedicated repository — see the note on
 * {@code ProcessDefinitionRepository} for why roles/tasks have no repository of their own.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CreateRoleDefinitionUseCase {

    private final ProcessDefinitionRepository repository;

    public RoleDefinition create(String orgKey, String processId, RoleDefinition role) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        if (process.findRole(role.getId()).isPresent()) {
            throw new ConflictException(
                    "Role '%s' already exists in process '%s'".formatted(role.getId(), processId));
        }
        process.addRole(role);
        repository.save(process);
        return role;
    }
}
