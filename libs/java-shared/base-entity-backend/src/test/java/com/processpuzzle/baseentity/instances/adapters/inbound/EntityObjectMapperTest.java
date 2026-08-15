package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
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
    void toModel_mapsAllFields() {
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

        com.processpuzzle.baseentity.model.EntityObject model = mapper.toModel(entity);

        assertThat(model.getId()).isEqualTo(id);
        assertThat(model.getEntityDefinitionCode()).isEqualTo("partner");
        assertThat(model.getVersion()).isEqualTo(1);
        assertThat(model.getPayload()).isEqualTo(Map.of("name", "ACME Corp"));
        assertThat(model.getCreatedAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
        assertThat(model.getCreatedBy()).isEqualTo("admin");
    }
}
