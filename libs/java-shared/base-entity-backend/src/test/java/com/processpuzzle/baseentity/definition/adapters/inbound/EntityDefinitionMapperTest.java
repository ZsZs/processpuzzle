package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityAttributeDto;
import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityDefinitionDto;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
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
    void fromDto_and_toDto_roundTrip() {
        BaseEntityDefinitionDto dto = BaseEntityDefinitionDto.builder()
                .code("partner")
                .name("Partner")
                .description("Partner entity")
                .status(EntityDefinitionStatus.ACTIVE)
                .componentParents(List.of())
                .isEmbedded(false)
                .attributes(List.of(
                        BaseEntityAttributeDto.builder()
                                .code("name")
                                .name("Name")
                                .displayOrder(1)
                                .valueKind(ValueKind.TEXT)
                                .formControlType(FormControlType.TEXT)
                                .required(true)
                                .build()
                ))
                .build();

        BaseEntityDefinition domain = mapper.fromDto(dto);
        assertThat(domain.getCode()).isEqualTo("partner");
        assertThat(domain.getAttributes()).hasSize(1);
        assertThat(domain.getAttributes().get(0).getEntityDefinition()).isSameAs(domain);

        BaseEntityDefinitionDto mappedBack = mapper.toDto(domain);
        assertThat(mappedBack.getCode()).isEqualTo("partner");
        assertThat(mappedBack.getAttributes()).hasSize(1);
    }
}
