package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class AddAttributeUseCase {

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionValidator validator;

    public BaseEntityAttribute addAttribute(String definitionCode, BaseEntityAttribute attribute) {
        BaseEntityDefinition definition = repository.findByCode(definitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(definitionCode)));

        boolean alreadyExists = definition.getAttributes().stream()
            .anyMatch(a -> a.getCode().equals(attribute.getCode()));
        if (alreadyExists) {
            throw new ConflictException(
                "Attribute '%s' already exists on '%s'".formatted(attribute.getCode(), definitionCode));
        }

        definition.addAttribute(attribute);
        validator.validate(definition);
        repository.save(definition);
        return attribute;
    }
}
