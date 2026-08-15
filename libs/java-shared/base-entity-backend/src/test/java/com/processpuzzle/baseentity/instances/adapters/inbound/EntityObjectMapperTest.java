package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.adapters.inbound.dto.EntityObjectDto;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityObjectMapperTest {

    private EntityObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new EntityObjectMapper();
    }

    @Test
    void toDto_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .version(1L)
                .payload(Map.of("name", "ACME Corp"))
                .build();
        entity.setCreatedAt(now);
        entity.setCreatedBy("admin");
        entity.setUpdatedAt(now);
        entity.setUpdatedBy("admin");

        EntityObjectDto dto = mapper.toDto(entity);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getEntityDefinitionCode()).isEqualTo("partner");
        assertThat(dto.getVersion()).isEqualTo(1L);
        assertThat(dto.getPayload()).isEqualTo(Map.of("name", "ACME Corp"));
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getCreatedBy()).isEqualTo("admin");
    }
}
