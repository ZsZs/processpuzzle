package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a role to the organization's catalog. Nothing is checked against the workflows that may
 * later reference it: a role exists on its own, and it is
 * {@code WorkflowValidator} that refuses a workflow naming one that does not.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CreateRoleDefinitionUseCase {

    private final RoleDefinitionRepository repository;

    public RoleDefinition create(String orgKey, RoleDefinition role) {
        role.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, role.getId())) {
            throw new ConflictException("Role definition '%s' already exists".formatted(role.getId()));
        }
        return repository.save(role);
    }
}
