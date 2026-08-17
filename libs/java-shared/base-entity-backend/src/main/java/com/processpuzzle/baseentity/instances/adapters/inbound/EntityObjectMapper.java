package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.model.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Maps domain EntityObject to OpenAPI generated model classes. */
@Component
public class EntityObjectMapper {

    public com.processpuzzle.baseentity.model.EntityObject toModel(EntityObject entityObject) {
        if (entityObject == null) {
            return null;
        }
        com.processpuzzle.baseentity.model.EntityObject model = new com.processpuzzle.baseentity.model.EntityObject();
        model.setId(entityObject.getId());
        model.setEntityDefinitionCode(entityObject.getEntityDefinitionCode());
        model.setVersion(entityObject.getVersion() != null ? entityObject.getVersion().intValue() : null);
        model.setPayload(entityObject.getPayload());
        model.setCreatedAt(toOffsetDateTime(entityObject.getCreatedAt()));
        model.setCreatedBy(entityObject.getCreatedBy());
        model.setUpdatedAt(toOffsetDateTime(entityObject.getUpdatedAt()));
        model.setUpdatedBy(entityObject.getUpdatedBy());
        return model;
    }

    public Page toPage(org.springframework.data.domain.Page<EntityObject> page) {
        List<Object> content = page.getContent().stream()
            .map(this::toModel)
            .map(Object.class::cast)
            .toList();
        return new Page()
            .content(content)
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements((int) page.getTotalElements())
            .totalPages(page.getTotalPages());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
