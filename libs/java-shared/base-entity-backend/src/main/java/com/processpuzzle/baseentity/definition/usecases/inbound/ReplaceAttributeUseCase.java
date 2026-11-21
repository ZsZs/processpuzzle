package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
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
public class ReplaceAttributeUseCase {

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionValidator validator;

    public BaseEntityAttribute replaceAttribute(String orgKey, String definitionCode, String attributeCode, BaseEntityAttribute desiredState) {
        BaseEntityDefinition definition = repository.findByOrgKeyAndCodeAndChannel(orgKey, definitionCode, Channel.DRAFT)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(definitionCode)));

        BaseEntityAttribute existing = definition.getAttributes().stream()
            .filter(a -> a.getCode().equals(attributeCode))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "'%s' has no attribute '%s'".formatted(definitionCode, attributeCode)));

        // Every authored field, through the one place that knows them all: a field listed here by hand is a field
        // silently dropped the day someone adds one. The attribute keeps the code it was addressed by.
        existing.copyFrom(desiredState);
        existing.setCode(attributeCode);

        validator.validate(definition);
        repository.save(definition);
        return existing;
    }
}
