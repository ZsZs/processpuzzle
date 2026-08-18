package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceRoleDefinitionUseCase {

    private final ProcessDefinitionRepository repository;

    public RoleDefinition replace(String orgKey, String processId, String roleId, RoleDefinition desiredState) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        RoleDefinition existing = process.findRole(roleId)
                .orElseThrow(() -> new NotFoundException(
                        "No role '%s' in process '%s'".formatted(roleId, processId)));

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setEntityRoleId(desiredState.getEntityRoleId());
        repository.save(process);
        return existing;
    }
}
