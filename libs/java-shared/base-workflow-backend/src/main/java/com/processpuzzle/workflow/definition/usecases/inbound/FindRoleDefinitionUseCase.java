package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindRoleDefinitionUseCase {

    private final RoleDefinitionRepository repository;

    @Transactional(readOnly = true)
    public RoleDefinition findByOrgKeyAndId(String orgKey, String id) {
        return repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No role definition with id '%s'".formatted(id)));
    }
}
