package com.processpuzzle.baseentity.api;

import java.util.Optional;

/**
 * Read-only questions about an entity <em>type</em>, for a module that has to check its own metadata
 * against base-entity's before persisting it.
 *
 * <p>base-state is the caller: a state machine names the attribute holding the current state, and
 * that name has to resolve to a real attribute of a kind that can hold a state key. Requiring it to
 * resolve is also what restricts state machines to entity types base-entity manages.
 */
public interface EntityAttributeQuery {

    /**
     * The kind of {@code attributeCode} on the entity type {@code entityDefinitionCode}.
     *
     * <p>Empty for both "no such entity type" and "no such attribute on it" — a caller validating a
     * reference wants the same answer either way, and distinguishing them would only invite a
     * message that leaks which of the two the deployment is missing.
     */
    Optional<EntityAttributeKind> attributeKind(String entityDefinitionCode, String attributeCode);

    /** Whether {@code entityDefinitionCode} resolves to an entity type at all. */
    boolean entityTypeExists(String entityDefinitionCode);
}
