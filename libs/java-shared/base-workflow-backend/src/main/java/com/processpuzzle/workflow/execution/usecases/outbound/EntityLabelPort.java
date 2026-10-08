package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Optional;

/**
 * Names the base-entity object a workflow instance runs for — {@code ORD-1001} rather than its UUID — so
 * the instance screens can show its subject the way the entity's own list does.
 *
 * <p>Supplied by the host application, from base-entity, which this module does not depend on. Without one
 * {@link #NONE} answers, and the instance shows only the id.
 */
public interface EntityLabelPort {

    /** Names nothing; the fallback when the host supplies no adapter. */
    EntityLabelPort NONE = (orgKey, entityType, entityId) -> Optional.empty();

    /**
     * @param entityType the base-entity definition code, e.g. {@code order}
     * @return the object's name; empty when the type or the object is unknown, or names nothing
     */
    Optional<String> labelOf(String orgKey, String entityType, String entityId);
}
