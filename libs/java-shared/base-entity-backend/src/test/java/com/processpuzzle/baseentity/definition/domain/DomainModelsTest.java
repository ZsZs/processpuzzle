package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainModelsTest {

    @Test
    void baseEntityDefinition_propertiesAndMethods() {
        UUID id1 = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        BaseEntityDefinition def = BaseEntityDefinition.builder()
                .id(id1)
                .code("partner")
                .name("Partner")
                .description("Partner desc")
                .version(1L)
                .status(EntityDefinitionStatus.ACTIVE)
                .componentParents(new ArrayList<>(List.of("parent-org")))
                .isEmbedded(true)
                .organizationId(orgId)
                .attributes(new ArrayList<>())
                .build();
        def.setCreatedAt(now);
        def.setCreatedBy("admin");
        def.setUpdatedAt(now);
        def.setUpdatedBy("admin");

        assertThat(def.getId()).isEqualTo(id1);
        assertThat(def.getCode()).isEqualTo("partner");
        assertThat(def.getName()).isEqualTo("Partner");
        assertThat(def.getDescription()).isEqualTo("Partner desc");
        assertThat(def.getVersion()).isEqualTo(1L);
        assertThat(def.getStatus()).isEqualTo(EntityDefinitionStatus.ACTIVE);
        assertThat(def.getComponentParents()).containsExactly("parent-org");
        assertThat(def.isEmbedded()).isTrue();
        assertThat(def.getOrganizationId()).isEqualTo(orgId);
        assertThat(def.getCreatedAt()).isEqualTo(now);
        assertThat(def.getCreatedBy()).isEqualTo("admin");
        assertThat(def.getUpdatedAt()).isEqualTo(now);
        assertThat(def.getUpdatedBy()).isEqualTo("admin");
        assertThat(def.isComponent()).isTrue();

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").name("Email").build();
        def.addAttribute(attr);
        assertThat(def.getAttributes()).containsExactly(attr);
        assertThat(attr.getEntityDefinition()).isSameAs(def);

        BaseEntityDefinition defSameId = BaseEntityDefinition.builder().id(id1).code("other").build();
        BaseEntityDefinition defDiffId = BaseEntityDefinition.builder().id(UUID.randomUUID()).code("partner").build();

        assertThat(def).isEqualTo(defSameId);
        assertThat(def.hashCode()).isEqualTo(defSameId.hashCode());
        assertThat(def).isNotEqualTo(defDiffId);
        assertThat(def.toString()).contains("partner").contains("Partner");

        BaseEntityDefinition emptyDef = new BaseEntityDefinition();
        assertThat(emptyDef.isComponent()).isFalse();
    }

    @Test
    void baseEntityAttribute_propertiesAndMethods() {
        UUID id = UUID.randomUUID();
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").build();

        BaseEntityAttribute attr = BaseEntityAttribute.builder()
                .id(id)
                .entityDefinition(def)
                .code("status")
                .name("Status")
                .displayOrder(2)
                .valueKind(ValueKind.ENUM)
                .formControlType(FormControlType.ENUM_SELECT)
                .isMultiValued(true)
                .required(true)
                .indexed(true)
                .defaultValue("ACTIVE")
                .enumValues(List.of("ACTIVE", "INACTIVE"))
                .linkedEntityType("some-type")
                .isLinkToDetails(true)
                .validationRules(Map.of("min", 1))
                .build();

        assertThat(attr.getId()).isEqualTo(id);
        assertThat(attr.getEntityDefinition()).isEqualTo(def);
        assertThat(attr.getCode()).isEqualTo("status");
        assertThat(attr.getName()).isEqualTo("Status");
        assertThat(attr.getDisplayOrder()).isEqualTo(2);
        assertThat(attr.getValueKind()).isEqualTo(ValueKind.ENUM);
        assertThat(attr.getFormControlType()).isEqualTo(FormControlType.ENUM_SELECT);
        assertThat(attr.isMultiValued()).isTrue();
        assertThat(attr.isRequired()).isTrue();
        assertThat(attr.isIndexed()).isTrue();
        assertThat(attr.getDefaultValue()).isEqualTo("ACTIVE");
        assertThat(attr.getEnumValues()).containsExactly("ACTIVE", "INACTIVE");
        assertThat(attr.getLinkedEntityType()).isEqualTo("some-type");
        assertThat(attr.isLinkToDetails()).isTrue();
        assertThat(attr.getValidationRules()).isEqualTo(Map.of("min", 1));

        BaseEntityAttribute attrSameId = BaseEntityAttribute.builder().id(id).code("other").build();
        assertThat(attr).isEqualTo(attrSameId);
        assertThat(attr.hashCode()).isEqualTo(attrSameId.hashCode());
        assertThat(attr.toString()).contains("status").contains("Status");

        BaseEntityAttribute noArg = new BaseEntityAttribute();
        noArg.setCode("code");
        assertThat(noArg.getCode()).isEqualTo("code");
    }

    @Test
    void attributeDescriptor_constructorsAndProperties() {
        AttributeDescriptor desc1 = new AttributeDescriptor("title", FormControlType.TEXT_BOX);
        assertThat(desc1.getAttrName()).isEqualTo("title");
        assertThat(desc1.getFormControlType()).isEqualTo(FormControlType.TEXT_BOX);
        assertThat(desc1.getVisible()).isTrue();
        assertThat(desc1.getShowThumbnail()).isTrue();
        assertThat(desc1.getHideInTable()).isFalse();
        assertThat(desc1.getReferenceIdField()).isEqualTo("id");

        AttributeDescriptor desc2 = new AttributeDescriptor("title", FormControlType.TEXT_BOX, "Title Label");
        assertThat(desc2.getLabel()).isEqualTo("Title Label");

        Selectable sel = new Selectable("key1", "val1");
        assertThat(sel.getKey()).isEqualTo("key1");
        assertThat(sel.getValue()).isEqualTo("val1");
        assertThat(sel.toString()).contains("key1");

        AttributeDescriptor desc3 = AttributeDescriptor.builder()
                .attrName("desc")
                .formControlType(FormControlType.TEXTAREA)
                .disabled(true)
                .style(Map.of("width", "100%"))
                .labelKey("label.key")
                .description("Attribute description")
                .styleClass("my-class")
                .labelClass("label-class")
                .format("yyyy-MM-dd")
                .isLinkToDetails(true)
                .selectables(List.of(sel))
                .visible(true)
                .showThumbnail(false)
                .hideInTable(true)
                .isHeading(true)
                .placeholder("Enter text...")
                .lines(5)
                .options(Map.of("opt", 1))
                .required(true)
                .pattern("[A-Z]+")
                .referenceIdField("refId")
                .linkedEntityType("child-entity")
                .build();

        assertThat(desc3.isDisabled()).isTrue();
        assertThat(desc3.getDescription()).isEqualTo("Attribute description");
        assertThat(desc3.getStyleClass()).isEqualTo("my-class");
        assertThat(desc3.getLabelClass()).isEqualTo("label-class");
        assertThat(desc3.getFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(desc3.isLinkToDetails()).isTrue();
        assertThat(desc3.getSelectables()).containsExactly(sel);
        assertThat(desc3.getShowThumbnail()).isFalse();
        assertThat(desc3.getHideInTable()).isTrue();
        assertThat(desc3.getIsHeading()).isTrue();
        assertThat(desc3.getPlaceholder()).isEqualTo("Enter text...");
        assertThat(desc3.getLines()).isEqualTo(5);
        assertThat(desc3.getOptions()).isEqualTo(Map.of("opt", 1));
        assertThat(desc3.isRequired()).isTrue();
        assertThat(desc3.getPattern()).isEqualTo("[A-Z]+");
        assertThat(desc3.getReferenceIdField()).isEqualTo("refId");
        assertThat(desc3.getLinkedEntityType()).isEqualTo("child-entity");

        assertThat(desc3.equals(desc3)).isTrue();
        assertThat(desc3.hashCode()).isNotNull();
        assertThat(desc3.toString()).contains("desc");
    }

    @Test
    void flexBoxContainerAndDescriptor_propertiesAndMethods() {
        AttributeDescriptor attr = new AttributeDescriptor("field", FormControlType.TEXT);
        FlexBoxContainer container = new FlexBoxContainer(null, FlexDirection.COLUMN);
        assertThat(container.getDirection()).isEqualTo(FlexDirection.COLUMN);
        assertThat(container.getAttrDescriptors()).isEmpty();

        container.addAttrDescriptor(attr);
        assertThat(container.getAttrDescriptors()).containsExactly(attr);

        FlexBoxContainer container2 = new FlexBoxContainer();
        container2.setDirection(FlexDirection.ROW);
        container2.setAttrDescriptors(null);
        container2.addAttrDescriptor(attr);
        assertThat(container2.getAttrDescriptors()).containsExactly(attr);

        FlexBoxDescriptor desc = new FlexBoxDescriptor(List.of(attr), FlexDirection.CONTAINER);
        assertThat(desc.getDirection()).isEqualTo(FlexDirection.CONTAINER);
        assertThat(desc.getAttrDescriptors()).containsExactly(attr);

        FlexBoxDescriptor emptyDesc = new FlexBoxDescriptor();
        assertThat(emptyDesc).isNotNull();
    }

    @Test
    void baseEntityDescriptor_propertiesAndMethods() {
        BaseEntityDescriptor desc = new BaseEntityDescriptor();
        desc.setEntityName("TestEntity");
        assertThat(desc.getEntityName()).isEqualTo("TestEntity");

        AttributeDescriptor attr = new AttributeDescriptor("field", FormControlType.TEXT);
        desc.setAttrDescriptors(null);
        desc.addAttrDescriptor(attr);
        assertThat(desc.getAttrDescriptors()).containsExactly(attr);

        BaseEntityDescriptor desc2 = BaseEntityDescriptor.builder()
                .entityName("Test2")
                .attrDescriptors(new ArrayList<>(List.of(attr)))
                .build();
        assertThat(desc2.getEntityName()).isEqualTo("Test2");
        assertThat(desc2.toString()).contains("Test2");
    }

    @Test
    void entityObject_propertiesAndMethods() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("k", "v"))
                .build();
        entity.setCreatedAt(now);
        entity.setCreatedBy("system");
        entity.setUpdatedAt(now);
        entity.setUpdatedBy("system");

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getEntityDefinitionCode()).isEqualTo("partner");
        assertThat(entity.getVersion()).isEqualTo(1L);
        assertThat(entity.getPayload()).isEqualTo(Map.of("k", "v"));
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getCreatedBy()).isEqualTo("system");

        EntityObject entitySameId = EntityObject.builder().id(id).build();
        assertThat(entity).isEqualTo(entitySameId);
        assertThat(entity.hashCode()).isEqualTo(entitySameId.hashCode());
        assertThat(entity.toString()).contains("partner");

        EntityObject emptyEntity = new EntityObject();
        assertThat(emptyEntity.getPayload()).isNotNull().isEmpty();
    }

    @Test
    void allEnums_valuesAndValueOf() {
        for (FlexDirection d : FlexDirection.values()) {
            assertThat(FlexDirection.valueOf(d.name())).isEqualTo(d);
        }
        for (FormControlType t : FormControlType.values()) {
            assertThat(FormControlType.valueOf(t.name())).isEqualTo(t);
        }
        for (ValueKind k : ValueKind.values()) {
            assertThat(ValueKind.valueOf(k.name())).isEqualTo(k);
        }
        for (EntityDefinitionStatus s : EntityDefinitionStatus.values()) {
            assertThat(EntityDefinitionStatus.valueOf(s.name())).isEqualTo(s);
        }
    }
}
