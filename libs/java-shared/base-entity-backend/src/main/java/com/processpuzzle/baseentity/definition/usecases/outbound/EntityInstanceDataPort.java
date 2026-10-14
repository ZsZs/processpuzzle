package com.processpuzzle.baseentity.definition.usecases.outbound;

import java.util.List;
import java.util.Map;

/**
 * What the objects of an entity type hold, for judging whether a changed definition still describes them. Like
 * {@link EntityInstanceExistenceCheckPort}, the definition module's way into the instances module.
 */
public interface EntityInstanceDataPort {

    /** The payload of every object of {@code entityDefinitionCode}; empty when it has none. */
    List<Map<String, Object>> payloadsOf(String orgKey, String entityDefinitionCode);
}
