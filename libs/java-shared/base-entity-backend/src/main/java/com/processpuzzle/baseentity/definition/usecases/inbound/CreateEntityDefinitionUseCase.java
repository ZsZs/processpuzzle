package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateEntityDefinitionUseCase {

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionValidator validator;

    public BaseEntityDefinition create(BaseEntityDefinition definition) {
        if (repository.existsByCode(definition.getCode())) {
            throw new ConflictException("Entity definition '%s' already exists".formatted(definition.getCode()));
        }
        validator.validate(definition);
        return repository.save(definition);
    }
}
