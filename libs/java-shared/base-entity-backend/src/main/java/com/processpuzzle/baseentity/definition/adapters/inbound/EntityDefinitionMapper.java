package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityAttributeDto;
import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityDefinitionDto;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import org.springframework.stereotype.Component;

/** dtoToEntity / entityToDto for the definition module's inbound (REST) adapter. */
@Component
public class EntityDefinitionMapper {

    public BaseEntityDefinition fromDto(BaseEntityDefinitionDto dto) {
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
            .code(dto.getCode())
            .name(dto.getName())
            .description(dto.getDescription())
            .status(dto.getStatus())
            .componentParents(dto.getComponentParents() != null ? dto.getComponentParents() : java.util.List.of())
            .isEmbedded(dto.isEmbedded())
            .organizationId(dto.getOrganizationId())
            .build();

        if (dto.getAttributes() != null) {
            dto.getAttributes().forEach(attrDto -> definition.addAttribute(fromDto(attrDto)));
        }
        return definition;
    }

    public BaseEntityAttribute fromDto(BaseEntityAttributeDto dto) {
        return BaseEntityAttribute.builder()
            .code(dto.getCode())
            .name(dto.getName())
            .displayOrder(dto.getDisplayOrder())
            .valueKind(dto.getValueKind())
            .formControlType(dto.getFormControlType())
            .isMultiValued(dto.isMultiValued())
            .required(dto.isRequired())
            .indexed(dto.isIndexed())
            .defaultValue(dto.getDefaultValue())
            .enumValues(dto.getEnumValues())
            .linkedEntityType(dto.getLinkedEntityType())
            .isLinkToDetails(dto.isLinkToDetails())
            .validationRules(dto.getValidationRules())
            .build();
    }

    public BaseEntityDefinitionDto toDto(BaseEntityDefinition definition) {
        return BaseEntityDefinitionDto.builder()
            .id(definition.getId())
            .code(definition.getCode())
            .name(definition.getName())
            .description(definition.getDescription())
            .version(definition.getVersion())
            .status(definition.getStatus())
            .componentParents(definition.getComponentParents())
            .isEmbedded(definition.isEmbedded())
            .organizationId(definition.getOrganizationId())
            .attributes(definition.getAttributes().stream().map(this::toDto).toList())
            .createdAt(definition.getCreatedAt())
            .createdBy(definition.getCreatedBy())
            .updatedAt(definition.getUpdatedAt())
            .updatedBy(definition.getUpdatedBy())
            .build();
    }

    public BaseEntityAttributeDto toDto(BaseEntityAttribute attribute) {
        return BaseEntityAttributeDto.builder()
            .id(attribute.getId())
            .code(attribute.getCode())
            .name(attribute.getName())
            .displayOrder(attribute.getDisplayOrder())
            .valueKind(attribute.getValueKind())
            .formControlType(attribute.getFormControlType())
            .isMultiValued(attribute.isMultiValued())
            .required(attribute.isRequired())
            .indexed(attribute.isIndexed())
            .defaultValue(attribute.getDefaultValue())
            .enumValues(attribute.getEnumValues())
            .linkedEntityType(attribute.getLinkedEntityType())
            .isLinkToDetails(attribute.isLinkToDetails())
            .validationRules(attribute.getValidationRules())
            .build();
    }
}
