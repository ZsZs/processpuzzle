package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
