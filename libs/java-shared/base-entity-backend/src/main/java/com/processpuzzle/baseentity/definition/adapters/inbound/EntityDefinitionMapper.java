package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.model.BaseEntityAttributeInput;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Maps domain objects to and from OpenAPI generated models for the definition module. */
@Component
public class EntityDefinitionMapper {

    public BaseEntityDefinition toDomain(BaseEntityDefinitionInput input) {
        if (input == null) {
            return null;
        }
        BaseEntityDefinition definition = BaseEntityDefinition.builder()
            .code(input.getCode())
            .name(input.getName())
            .description(input.getDescription())
            .status(toDomainStatus(input.getStatus()))
            .componentParents(input.getComponentParents() != null ? input.getComponentParents() : List.of())
            .isEmbedded(Boolean.TRUE.equals(input.getIsEmbedded()))
            .organizationId(input.getOrganizationId())
            .build();

        if (input.getAttributes() != null) {
            input.getAttributes().forEach(attrInput -> definition.addAttribute(toDomain(attrInput)));
        }
        return definition;
    }

    public BaseEntityAttribute toDomain(BaseEntityAttributeInput input) {
        if (input == null) {
            return null;
        }
        return BaseEntityAttribute.builder()
            .code(input.getCode())
            .name(input.getName())
            .displayOrder(input.getDisplayOrder() != null ? input.getDisplayOrder() : 0)
            .valueKind(toDomainValueKind(input.getValueKind()))
            .formControlType(toDomainFormControlType(input.getFormControlType()))
            .isMultiValued(Boolean.TRUE.equals(input.getIsMultiValued()))
            .required(Boolean.TRUE.equals(input.getRequired()))
            .indexed(Boolean.TRUE.equals(input.getIndexed()))
            .defaultValue(input.getDefaultValue())
            .enumValues(input.getEnumValues())
            .linkedEntityType(input.getLinkedEntityType())
            .isLinkToDetails(Boolean.TRUE.equals(input.getIsLinkToDetails()))
            .validationRules(input.getValidationRules())
            .build();
    }

    public com.processpuzzle.baseentity.model.BaseEntityDefinition toModel(BaseEntityDefinition definition) {
        if (definition == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.BaseEntityDefinition model = new com.processpuzzle.baseentity.model.BaseEntityDefinition();
        model.setId(definition.getId());
        model.setCode(definition.getCode());
        model.setName(definition.getName());
        model.setDescription(definition.getDescription());
        model.setVersion(definition.getVersion() != null ? definition.getVersion().intValue() : null);
        model.setStatus(toModelStatus(definition.getStatus()));
        model.setComponentParents(definition.getComponentParents());
        model.setIsEmbedded(definition.isEmbedded());
        model.setOrganizationId(definition.getOrganizationId());
        if (definition.getAttributes() != null) {
            model.setAttributes(definition.getAttributes().stream().map(this::toModel).toList());
        }
        model.setCreatedAt(toOffsetDateTime(definition.getCreatedAt()));
        model.setCreatedBy(definition.getCreatedBy());
        model.setUpdatedAt(toOffsetDateTime(definition.getUpdatedAt()));
        model.setUpdatedBy(definition.getUpdatedBy());
        return model;
    }

    public com.processpuzzle.baseentity.model.BaseEntityAttribute toModel(BaseEntityAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.BaseEntityAttribute model = new com.processpuzzle.baseentity.model.BaseEntityAttribute();
        model.setId(attribute.getId());
        model.setCode(attribute.getCode());
        model.setName(attribute.getName());
        model.setDisplayOrder(attribute.getDisplayOrder());
        model.setValueKind(toModelValueKind(attribute.getValueKind()));
        model.setFormControlType(toModelFormControlType(attribute.getFormControlType()));
        model.setIsMultiValued(attribute.isMultiValued());
        model.setRequired(attribute.isRequired());
        model.setIndexed(attribute.isIndexed());
        model.setDefaultValue(attribute.getDefaultValue());
        model.setEnumValues(attribute.getEnumValues());
        model.setLinkedEntityType(attribute.getLinkedEntityType());
        model.setIsLinkToDetails(attribute.isLinkToDetails());
        model.setValidationRules(attribute.getValidationRules());
        return model;
    }

    public Page toPage(org.springframework.data.domain.Page<BaseEntityDefinition> page) {
        List<Object> content = page.getContent().stream()
            .map(this::toModel)
            .map(item -> (Object) item)
            .toList();
        return new Page()
            .content(content)
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements((int) page.getTotalElements())
            .totalPages(page.getTotalPages());
    }

    public com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus toDomainStatus(com.processpuzzle.baseentity.model.EntityDefinitionStatus status) {
        return status == null ? null : com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus.valueOf(status.getValue());
    }

    public com.processpuzzle.baseentity.model.EntityDefinitionStatus toModelStatus(com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus status) {
        return status == null ? null : com.processpuzzle.baseentity.model.EntityDefinitionStatus.fromValue(status.name());
    }

    public com.processpuzzle.baseentity.definition.domain.ValueKind toDomainValueKind(com.processpuzzle.baseentity.model.ValueKind valueKind) {
        return valueKind == null ? null : com.processpuzzle.baseentity.definition.domain.ValueKind.valueOf(valueKind.getValue());
    }

    public com.processpuzzle.baseentity.model.ValueKind toModelValueKind(com.processpuzzle.baseentity.definition.domain.ValueKind valueKind) {
        return valueKind == null ? null : com.processpuzzle.baseentity.model.ValueKind.fromValue(valueKind.name());
    }

    public com.processpuzzle.baseentity.definition.domain.FormControlType toDomainFormControlType(com.processpuzzle.baseentity.model.FormControlType formControlType) {
        return formControlType == null ? null : com.processpuzzle.baseentity.definition.domain.FormControlType.valueOf(formControlType.getValue());
    }

    public com.processpuzzle.baseentity.model.FormControlType toModelFormControlType(com.processpuzzle.baseentity.definition.domain.FormControlType formControlType) {
        return formControlType == null ? null : com.processpuzzle.baseentity.model.FormControlType.fromValue(formControlType.name());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
