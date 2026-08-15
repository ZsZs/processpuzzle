package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.adapters.inbound.dto.EntityObjectDto;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import org.springframework.stereotype.Component;

/** dtoToEntity / entityToDto for the instances module's inbound (REST) adapter. */
@Component
public class EntityObjectMapper {

    public EntityObjectDto toDto(EntityObject entityObject) {
        return EntityObjectDto.builder()
            .id(entityObject.getId())
            .entityDefinitionCode(entityObject.getEntityDefinitionCode())
            .version(entityObject.getVersion())
            .payload(entityObject.getPayload())
            .createdAt(entityObject.getCreatedAt())
            .createdBy(entityObject.getCreatedBy())
            .updatedAt(entityObject.getUpdatedAt())
            .updatedBy(entityObject.getUpdatedBy())
            .build();
    }
}
