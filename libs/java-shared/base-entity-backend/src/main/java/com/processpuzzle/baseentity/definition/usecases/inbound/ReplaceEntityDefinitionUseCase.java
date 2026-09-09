package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

        replaceAttributes(existing, desiredState);

        validator.validate(existing);
        return repository.save(existing);
    }

    private void replaceAttributes(BaseEntityDefinition existing, BaseEntityDefinition desiredState) {
        Map<String, BaseEntityAttribute> existingByCode = existing.getAttributes().stream()
            .collect(Collectors.toMap(BaseEntityAttribute::getCode, Function.identity()));
        Set<String> desiredCodes = desiredState.getAttributes().stream()
            .map(BaseEntityAttribute::getCode)
            .collect(Collectors.toSet());

        existing.getAttributes().removeIf(attribute -> !desiredCodes.contains(attribute.getCode()));
        for (BaseEntityAttribute desired : desiredState.getAttributes()) {
            BaseEntityAttribute attribute = existingByCode.get(desired.getCode());
            if (attribute == null) {
                existing.addAttribute(desired);
                continue;
            }
            copyAttributeState(attribute, desired);
        }
    }

    private void copyAttributeState(BaseEntityAttribute existing, BaseEntityAttribute desired) {
        existing.setName(desired.getName());
        existing.setDisplayOrder(desired.getDisplayOrder());
        existing.setValueKind(desired.getValueKind());
        existing.setFormControlType(desired.getFormControlType());
        existing.setMultiValued(desired.isMultiValued());
        existing.setRequired(desired.isRequired());
        existing.setIndexed(desired.isIndexed());
        existing.setDefaultValue(desired.getDefaultValue());
        existing.setEnumValues(desired.getEnumValues());
        existing.setLinkedEntityType(desired.getLinkedEntityType());
        existing.setLinkToDetails(desired.isLinkToDetails());
        existing.setValidationRules(desired.getValidationRules());
    }
}
