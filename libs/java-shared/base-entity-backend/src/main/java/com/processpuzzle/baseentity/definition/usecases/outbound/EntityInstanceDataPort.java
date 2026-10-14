package com.processpuzzle.baseentity.definition.usecases.outbound;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * What the objects of an entity type hold, for judging whether a changed definition still describes them, and the
 * rewrite that migrates them to fit it. Like {@link EntityInstanceExistenceCheckPort}, the definition module's way into
 * the instances module.
 */
public interface EntityInstanceDataPort {

    /** The payload of every object of {@code entityDefinitionCode}; empty when it has none. */
    List<Map<String, Object>> payloadsOf(String orgKey, String entityDefinitionCode);

    /**
     * Rewrites every object's payload of {@code entityDefinitionCode} with {@code rewrite}, which gets a copy of each;
     * an object whose payload it leaves equal is not written.
     *
     * @return how many objects were written
     */
    long rewritePayloads(String orgKey, String entityDefinitionCode, UnaryOperator<Map<String, Object>> rewrite);
}
