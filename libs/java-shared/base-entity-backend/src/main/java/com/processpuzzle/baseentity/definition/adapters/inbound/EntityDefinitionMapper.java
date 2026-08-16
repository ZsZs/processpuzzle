package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.domain.*;
import com.processpuzzle.baseentity.model.BaseEntityAttributeInput;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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

    public EntityDefinitionStatus toDomainStatus(com.processpuzzle.baseentity.model.EntityDefinitionStatus status) {
        return status == null ? null : EntityDefinitionStatus.valueOf(status.getValue());
    }

    public com.processpuzzle.baseentity.model.EntityDefinitionStatus toModelStatus(EntityDefinitionStatus status) {
        return status == null ? null : com.processpuzzle.baseentity.model.EntityDefinitionStatus.fromValue(status.name());
    }

    public ValueKind toDomainValueKind(com.processpuzzle.baseentity.model.ValueKind valueKind) {
        return valueKind == null ? null : ValueKind.valueOf(valueKind.getValue());
    }

    public com.processpuzzle.baseentity.model.ValueKind toModelValueKind(ValueKind valueKind) {
        return valueKind == null ? null : com.processpuzzle.baseentity.model.ValueKind.fromValue(valueKind.name());
    }

    public FormControlType toDomainFormControlType(com.processpuzzle.baseentity.model.FormControlType formControlType) {
        return formControlType == null ? null : FormControlType.valueOf(formControlType.getValue());
    }

    public com.processpuzzle.baseentity.model.FormControlType toModelFormControlType(FormControlType formControlType) {
        return formControlType == null ? null : com.processpuzzle.baseentity.model.FormControlType.fromValue(formControlType.name());
    }

    public FlexDirection toDomainFlexDirection(com.processpuzzle.baseentity.model.FlexDirection direction) {
        return direction == null ? null : FlexDirection.valueOf(direction.getValue());
    }

    public com.processpuzzle.baseentity.model.FlexDirection toModelFlexDirection(FlexDirection direction) {
        return direction == null ? null : com.processpuzzle.baseentity.model.FlexDirection.fromValue(direction.name());
    }

    public Selectable toDomain(com.processpuzzle.baseentity.model.Selectable selectable) {
        if (selectable == null) {
            return null;
        }
        return Selectable.builder()
            .key(selectable.getKey())
            .value(selectable.getValue())
            .build();
    }

    public com.processpuzzle.baseentity.model.Selectable toModel(Selectable selectable) {
        if (selectable == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.Selectable model = new com.processpuzzle.baseentity.model.Selectable();
        model.setKey(selectable.getKey());
        model.setValue(selectable.getValue());
        return model;
    }

    public AbstractAttrDescriptor toDomain(com.processpuzzle.baseentity.model.AbstractAttrDescriptor input) {
        if (input == null) {
            return null;
        }
        if (input.getFormControlType() == com.processpuzzle.baseentity.model.FormControlType.FLEX_BOX) {
            return FlexBoxContainer.builder()
                .attrName(input.getAttrName())
                .formControlType(toDomainFormControlType(input.getFormControlType()))
                .disabled(Boolean.TRUE.equals(input.getDisabled()))
                .style(input.getStyle())
                .labelKey(input.getLabelKey())
                .direction(FlexDirection.CONTAINER)
                .attrDescriptors(new ArrayList<>())
                .build();
        }
        return AttributeDescriptor.builder()
            .attrName(input.getAttrName())
            .formControlType(toDomainFormControlType(input.getFormControlType()))
            .disabled(Boolean.TRUE.equals(input.getDisabled()))
            .style(input.getStyle())
            .labelKey(input.getLabelKey())
            .build();
    }

    public com.processpuzzle.baseentity.model.AbstractAttrDescriptor toModel(AbstractAttrDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.AbstractAttrDescriptor model = new com.processpuzzle.baseentity.model.AbstractAttrDescriptor();
        model.setAttrName(descriptor.getAttrName());
        model.setFormControlType(toModelFormControlType(descriptor.getFormControlType()));
        model.setDisabled(descriptor.isDisabled());
        model.setStyle(descriptor.getStyle());
        model.setLabelKey(descriptor.getLabelKey());
        return model;
    }

    public AttributeDescriptor toDomain(com.processpuzzle.baseentity.model.AttributeDescriptor input) {
        if (input == null) {
            return null;
        }
        return AttributeDescriptor.builder()
            .attrName(input.getAttrName())
            .formControlType(toDomainFormControlType(input.getFormControlType()))
            .disabled(Boolean.TRUE.equals(input.getDisabled()))
            .style(input.getStyle())
            .labelKey(input.getLabelKey())
            .label(input.getLabel())
            .description(input.getDescription())
            .styleClass(input.getStyleClass())
            .labelClass(input.getLabelClass())
            .format(input.getFormat())
            .isLinkToDetails(Boolean.TRUE.equals(input.getIsLinkToDetails()))
            .selectables(input.getSelectables() != null ? input.getSelectables().stream().map(this::toDomain).toList() : null)
            .visible(input.getVisible())
            .showThumbnail(input.getShowThumbnail())
            .hideInTable(input.getHideInTable())
            .isHeading(input.getIsHeading())
            .placeholder(input.getPlaceholder())
            .lines(input.getLines())
            .options(input.getOptions())
            .required(Boolean.TRUE.equals(input.getRequired()))
            .pattern(input.getPattern())
            .referenceIdField(input.getReferenceIdField())
            .linkedEntityType(input.getLinkedEntityType())
            .build();
    }

    public com.processpuzzle.baseentity.model.AttributeDescriptor toModel(AttributeDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.AttributeDescriptor model = new com.processpuzzle.baseentity.model.AttributeDescriptor();
        model.setAttrName(descriptor.getAttrName());
        model.setFormControlType(toModelFormControlType(descriptor.getFormControlType()));
        model.setDisabled(descriptor.isDisabled());
        model.setStyle(descriptor.getStyle());
        model.setLabelKey(descriptor.getLabelKey());
        model.setLabel(descriptor.getLabel());
        model.setDescription(descriptor.getDescription());
        model.setStyleClass(descriptor.getStyleClass());
        model.setLabelClass(descriptor.getLabelClass());
        model.setFormat(descriptor.getFormat());
        model.setIsLinkToDetails(descriptor.isLinkToDetails());
        if (descriptor.getSelectables() != null) {
            model.setSelectables(descriptor.getSelectables().stream().map(this::toModel).toList());
        }
        model.setVisible(descriptor.getVisible());
        model.setShowThumbnail(descriptor.getShowThumbnail());
        model.setHideInTable(descriptor.getHideInTable());
        model.setIsHeading(descriptor.getIsHeading());
        model.setPlaceholder(descriptor.getPlaceholder());
        model.setLines(descriptor.getLines());
        model.setOptions(descriptor.getOptions());
        model.setRequired(descriptor.isRequired());
        model.setPattern(descriptor.getPattern());
        model.setReferenceIdField(descriptor.getReferenceIdField());
        model.setLinkedEntityType(descriptor.getLinkedEntityType());
        return model;
    }

    public FlexBoxContainer toDomain(com.processpuzzle.baseentity.model.FlexBoxDescriptor input) {
        if (input == null) {
            return null;
        }
        List<AbstractAttrDescriptor> children = input.getAttrDescriptors() != null
            ? input.getAttrDescriptors().stream().map(this::toDomain).toList()
            : new ArrayList<>();
        return FlexBoxContainer.builder()
            .attrName(input.getAttrName() != null ? input.getAttrName() : "dummy")
            .formControlType(toDomainFormControlType(input.getFormControlType()))
            .disabled(Boolean.TRUE.equals(input.getDisabled()))
            .style(input.getStyle())
            .labelKey(input.getLabelKey())
            .direction(toDomainFlexDirection(input.getDirection()))
            .attrDescriptors(new ArrayList<>(children))
            .build();
    }

    public com.processpuzzle.baseentity.model.FlexBoxDescriptor toModel(FlexBoxContainer container) {
        if (container == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.FlexBoxDescriptor model = new com.processpuzzle.baseentity.model.FlexBoxDescriptor();
        model.setAttrName(container.getAttrName());
        model.setFormControlType(toModelFormControlType(container.getFormControlType()));
        model.setDisabled(container.isDisabled());
        model.setStyle(container.getStyle());
        model.setLabelKey(container.getLabelKey());
        model.setDirection(toModelFlexDirection(container.getDirection()));
        if (container.getAttrDescriptors() != null) {
            model.setAttrDescriptors(container.getAttrDescriptors().stream().map(this::toModel).toList());
        }
        return model;
    }

    public BaseEntityDescriptor toDomain(com.processpuzzle.baseentity.model.BaseEntityDescriptor input) {
        if (input == null) {
            return null;
        }
        List<AbstractAttrDescriptor> descriptors = input.getAttrDescriptors() != null
            ? input.getAttrDescriptors().stream().map(this::toDomain).toList()
            : new ArrayList<>();
        return BaseEntityDescriptor.builder()
            .entityName(input.getEntityName())
            .attrDescriptors(new ArrayList<>(descriptors))
            .build();
    }

    public com.processpuzzle.baseentity.model.BaseEntityDescriptor toModel(BaseEntityDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.BaseEntityDescriptor model = new com.processpuzzle.baseentity.model.BaseEntityDescriptor();
        model.setEntityName(descriptor.getEntityName());
        if (descriptor.getAttrDescriptors() != null) {
            model.setAttrDescriptors(descriptor.getAttrDescriptors().stream().map(this::toModel).toList());
        }
        return model;
    }

    public AttributeDescriptor toAttributeDescriptor(BaseEntityAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        return AttributeDescriptor.builder()
            .attrName(attribute.getCode())
            .label(attribute.getName())
            .formControlType(attribute.getFormControlType())
            .required(attribute.isRequired())
            .isLinkToDetails(attribute.isLinkToDetails())
            .linkedEntityType(attribute.getLinkedEntityType())
            .build();
    }

    public BaseEntityDescriptor toDescriptor(BaseEntityDefinition definition) {
        if (definition == null) {
            return null;
        }
        List<AbstractAttrDescriptor> attrDescriptors = definition.getAttributes() != null
            ? definition.getAttributes().stream().map(this::toAttributeDescriptor).map(a -> (AbstractAttrDescriptor) a).toList()
            : List.of();
        return BaseEntityDescriptor.builder()
            .entityName(definition.getName())
            .attrDescriptors(new ArrayList<>(attrDescriptors))
            .build();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
