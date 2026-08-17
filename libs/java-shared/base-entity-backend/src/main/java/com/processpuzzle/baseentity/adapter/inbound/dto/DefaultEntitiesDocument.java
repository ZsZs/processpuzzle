package com.processpuzzle.baseentity.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.EntityObjectInput;

import java.util.List;

/**
 * Root of a bundled {@code default-entities/<orgKey>-entities.yaml} file, read by
 * {@link com.processpuzzle.baseentity.adapter.inbound.DefaultEntityLoader} on startup.
 *
 * @param entityDefinitions the entity definitions to seed
 * @param entities the sample entity instances to seed
 */
public record DefaultEntitiesDocument(
        @JsonAlias({"entityDefinitions", "definitions"})
        List<BaseEntityDefinitionInput> entityDefinitions,

        @JsonAlias({"entities", "entityObjects", "sampleObjects", "instances"})
        List<EntityObjectInput> entities
) {
    public DefaultEntitiesDocument {
        entityDefinitions = entityDefinitions == null ? List.of() : List.copyOf(entityDefinitions);
        entities = entities == null ? List.of() : List.copyOf(entities);
    }
}
