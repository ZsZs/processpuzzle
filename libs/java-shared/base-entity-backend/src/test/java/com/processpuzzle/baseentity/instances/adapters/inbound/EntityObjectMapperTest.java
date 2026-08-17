package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void toModel_null_returnsNull() {
        assertThat(mapper.toModel(null)).isNull();
    }

    @Test
    void toModel_nullTimestampsAndVersion() {
        EntityObject entity = EntityObject.builder()
                .id(UUID.randomUUID())
                .entityDefinitionCode("partner")
                .version(null)
                .payload(Map.of())
                .build();

        com.processpuzzle.baseentity.model.EntityObject model = mapper.toModel(entity);
        assertThat(model).isNotNull();
        assertThat(model.getVersion()).isNull();
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    @Test
    void toPage_mapsPageCorrectly() {
        UUID id = UUID.randomUUID();
        EntityObject entity = EntityObject.builder()
                .id(id)
                .entityDefinitionCode("partner")
                .build();
        org.springframework.data.domain.Page<EntityObject> springPage =
                new org.springframework.data.domain.PageImpl<>(List.of(entity), org.springframework.data.domain.PageRequest.of(0, 5), 1);

        com.processpuzzle.baseentity.model.Page page = mapper.toPage(springPage);
        assertThat(page.getPage()).isEqualTo(0);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }
}
