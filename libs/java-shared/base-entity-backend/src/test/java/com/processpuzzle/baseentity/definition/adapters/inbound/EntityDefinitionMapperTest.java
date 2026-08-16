package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.FlexBoxContainer;
import com.processpuzzle.baseentity.definition.domain.FlexBoxDescriptor;
import com.processpuzzle.baseentity.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityDefinitionMapperTest {

    private EntityDefinitionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EntityDefinitionMapper();
    }

    @Test
    void toDomain_and_toModel_roundTrip() {
        BaseEntityAttributeInput attrInput = new BaseEntityAttributeInput();
        attrInput.setCode("name");
        attrInput.setName("Name");
        attrInput.setDisplayOrder(1);
        attrInput.setValueKind(ValueKind.TEXT);
        attrInput.setFormControlType(FormControlType.TEXT);
        attrInput.setRequired(true);

        BaseEntityDefinitionInput input = new BaseEntityDefinitionInput();
        input.setCode("partner");
        input.setName("Partner");
        input.setDescription("Partner entity");
        input.setStatus(EntityDefinitionStatus.ACTIVE);
        input.setComponentParents(List.of());
        input.setIsEmbedded(false);
        input.setAttributes(List.of(attrInput));

        BaseEntityDefinition domain = mapper.toDomain(input);
        assertThat(domain.getCode()).isEqualTo("partner");
        assertThat(domain.getAttributes()).hasSize(1);
        assertThat(domain.getAttributes().get(0).getEntityDefinition()).isSameAs(domain);

        com.processpuzzle.baseentity.model.BaseEntityDefinition model = mapper.toModel(domain);
        assertThat(model.getCode()).isEqualTo("partner");
        assertThat(model.getAttributes()).hasSize(1);
        assertThat(model.getAttributes().get(0).getCode()).isEqualTo("name");
    }

    @Test
    void flexBoxContainer_roundTrip() {
        // Build a nested layout: container with 2 columns containing attributes
        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor nameAttr =
                com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.builder()
                        .attrName("name")
                        .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.TEXT_BOX)
                        .label("Name")
                        .required(true)
                        .isLinkToDetails(true)
                        .build();

        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor descriptionAttr =
                com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.builder()
                        .attrName("description")
                        .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.TEXTAREA)
                        .label("Description")
                        .build();

        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor numberAttr =
                com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.builder()
                        .attrName("number")
                        .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.NUMBER)
                        .label("Number")
                        .build();

        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor enumAttr =
                com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.builder()
                        .attrName("enumValue")
                        .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.DROPDOWN)
                        .label("Enum")
                        .selectables(List.of(new com.processpuzzle.baseentity.definition.domain.Selectable("1", "Alpha")))
                        .build();

        FlexBoxContainer col1 = new FlexBoxContainer(
                List.of(nameAttr, descriptionAttr),
                com.processpuzzle.baseentity.definition.domain.FlexDirection.COLUMN
        );
        FlexBoxContainer col2 = new FlexBoxContainer(
                List.of(numberAttr, enumAttr),
                com.processpuzzle.baseentity.definition.domain.FlexDirection.COLUMN
        );

        FlexBoxContainer flexBoxContainer = new FlexBoxContainer(
                List.of(col1, col2),
                com.processpuzzle.baseentity.definition.domain.FlexDirection.CONTAINER
        );
        flexBoxContainer.setStyle(Map.of("column-gap", "20px"));

        // Domain to Model
        com.processpuzzle.baseentity.model.FlexBoxDescriptor model = mapper.toModel(flexBoxContainer);
        assertThat(model.getDirection()).isEqualTo(FlexDirection.CONTAINER);
        assertThat(model.getStyle()).containsEntry("column-gap", "20px");
        assertThat(model.getAttrDescriptors()).hasSize(2);

        // Model to Domain
        FlexBoxContainer domainFromModel = mapper.toDomain(model);
        assertThat(domainFromModel.getDirection()).isEqualTo(com.processpuzzle.baseentity.definition.domain.FlexDirection.CONTAINER);
        assertThat(domainFromModel.getStyle()).containsEntry("column-gap", "20px");
        assertThat(domainFromModel.getAttrDescriptors()).hasSize(2);
        assertThat(domainFromModel.getAttrDescriptors().get(0)).isInstanceOf(FlexBoxContainer.class);
    }

    @Test
    void flexBoxDescriptor_aliasWorks() {
        FlexBoxDescriptor descriptor = new FlexBoxDescriptor(List.of(), com.processpuzzle.baseentity.definition.domain.FlexDirection.ROW);
        assertThat(descriptor.getDirection()).isEqualTo(com.processpuzzle.baseentity.definition.domain.FlexDirection.ROW);
        assertThat(descriptor.getFormControlType()).isEqualTo(com.processpuzzle.baseentity.definition.domain.FormControlType.FLEX_BOX);

        com.processpuzzle.baseentity.model.FlexBoxDescriptor model = mapper.toModel((FlexBoxContainer) descriptor);
        assertThat(model.getDirection()).isEqualTo(FlexDirection.ROW);
    }

    @Test
    void baseEntityDescriptor_roundTrip() {
        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor attr =
                com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.builder()
                        .attrName("code")
                        .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.TEXT)
                        .label("Code")
                        .build();

        FlexBoxContainer container = new FlexBoxContainer(List.of(attr), com.processpuzzle.baseentity.definition.domain.FlexDirection.COLUMN);

        com.processpuzzle.baseentity.definition.domain.BaseEntityDescriptor entityDescriptor =
                com.processpuzzle.baseentity.definition.domain.BaseEntityDescriptor.builder()
                        .entityName("Test Entity")
                        .attrDescriptors(List.of(container))
                        .build();

        com.processpuzzle.baseentity.model.BaseEntityDescriptor model = mapper.toModel(entityDescriptor);
        assertThat(model.getEntityName()).isEqualTo("Test Entity");
        assertThat(model.getAttrDescriptors()).hasSize(1);

        com.processpuzzle.baseentity.definition.domain.BaseEntityDescriptor fromModel = mapper.toDomain(model);
        assertThat(fromModel.getEntityName()).isEqualTo("Test Entity");
        assertThat(fromModel.getAttrDescriptors()).hasSize(1);
    }

    @Test
    void toDescriptor_fromBaseEntityDefinition() {
        BaseEntityAttribute attr = BaseEntityAttribute.builder()
                .code("title")
                .name("Title")
                .formControlType(com.processpuzzle.baseentity.definition.domain.FormControlType.TEXT_BOX)
                .required(true)
                .isLinkToDetails(true)
                .build();

        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .code("article")
                .name("Article")
                .attributes(List.of(attr))
                .build();

        com.processpuzzle.baseentity.definition.domain.BaseEntityDescriptor desc = mapper.toDescriptor(def);
        assertThat(desc.getEntityName()).isEqualTo("Article");
        assertThat(desc.getAttrDescriptors()).hasSize(1);
        assertThat(desc.getAttrDescriptors().get(0)).isInstanceOf(com.processpuzzle.baseentity.definition.domain.AttributeDescriptor.class);
        com.processpuzzle.baseentity.definition.domain.AttributeDescriptor attrDesc =
                (com.processpuzzle.baseentity.definition.domain.AttributeDescriptor) desc.getAttrDescriptors().get(0);
        assertThat(attrDesc.getAttrName()).isEqualTo("title");
        assertThat(attrDesc.getLabel()).isEqualTo("Title");
        assertThat(attrDesc.isRequired()).isTrue();
        assertThat(attrDesc.isLinkToDetails()).isTrue();
    }

    @Test
    void allNewFormControlTypes_mapCorrectly() {
        for (com.processpuzzle.baseentity.definition.domain.FormControlType domainType :
                com.processpuzzle.baseentity.definition.domain.FormControlType.values()) {
            FormControlType modelType = mapper.toModelFormControlType(domainType);
            assertThat(modelType).isNotNull();
            assertThat(mapper.toDomainFormControlType(modelType)).isEqualTo(domainType);
        }
    }
}
