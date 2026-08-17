package com.processpuzzle.baseentity.definition.usecases.inbound;

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

    public BaseEntityAttribute replaceAttribute(String definitionCode, String attributeCode, BaseEntityAttribute desiredState) {
        BaseEntityDefinition definition = repository.findByCode(definitionCode)
            .orElseThrow(() -> new NotFoundException("No entity definition with code '%s'".formatted(definitionCode)));

        BaseEntityAttribute existing = definition.getAttributes().stream()
            .filter(a -> a.getCode().equals(attributeCode))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "'%s' has no attribute '%s'".formatted(definitionCode, attributeCode)));

        existing.setName(desiredState.getName());
        existing.setDisplayOrder(desiredState.getDisplayOrder());
        existing.setValueKind(desiredState.getValueKind());
        existing.setFormControlType(desiredState.getFormControlType());
        existing.setMultiValued(desiredState.isMultiValued());
        existing.setRequired(desiredState.isRequired());
        existing.setIndexed(desiredState.isIndexed());
        existing.setDefaultValue(desiredState.getDefaultValue());
        existing.setEnumValues(desiredState.getEnumValues());
        existing.setLinkedEntityType(desiredState.getLinkedEntityType());
        existing.setLinkToDetails(desiredState.isLinkToDetails());
        existing.setValidationRules(desiredState.getValidationRules());

        validator.validate(definition);
        repository.save(definition);
        return existing;
    }
}
