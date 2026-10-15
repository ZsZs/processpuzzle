package com.processpuzzle.baseentity.port;

import java.util.List;
import java.util.Map;

/**
 * The rules an entity object must satisfy before it is saved — today, base-rule's ERROR rules of the entity's
 * context. Asked on every create and update, whichever path the change took: an owner form's Save, an embedded
 * child's save that rewrites the whole aggregate, or a workflow step.
 *
 * <p>base-entity names no module here; the application's composition root supplies the adapter. Without one no
 * rule is enforced, which is how base-entity behaves on its own.
 */
public interface EntityRulesPort {

    EntityRulesPort NONE = (orgKey, entityName, payload) -> List.of();

    /**
     * @param entityName the definition's name, e.g. {@code Order} — what a rule's context names, not the code
     * @return the messages of the blocking violations; empty when the object may be saved
     */
    List<String> blockingViolationsOf(String orgKey, String entityName, Map<String, Object> payload);
}
