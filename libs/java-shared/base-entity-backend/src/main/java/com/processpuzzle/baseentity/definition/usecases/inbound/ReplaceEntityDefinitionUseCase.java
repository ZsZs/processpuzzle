package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ReplaceEntityDefinitionUseCase {

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionValidator validator;

    public BaseEntityDefinition replace(String code, BaseEntityDefinition desiredState) {
        BaseEntityDefinition existing = repository.findByCode(code)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(code)));

        if (!code.equals(desiredState.getCode())) {
            throw new ConflictException("code is immutable — cannot rename '%s' to '%s'".formatted(code, desiredState.getCode()));
        }

        existing.setName(desiredState.getName());
        existing.setDescription(desiredState.getDescription());
        existing.setStatus(desiredState.getStatus());
        existing.setComponentParents(desiredState.getComponentParents());
        existing.setEmbedded(desiredState.isEmbedded());
        existing.setOrganizationId(desiredState.getOrganizationId());

        existing.getAttributes().clear();
        desiredState.getAttributes().forEach(existing::addAttribute);

        validator.validate(existing);
        return repository.save(existing);
    }
}
