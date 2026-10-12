package com.processpuzzle.baseentity.api;

import com.processpuzzle.core.definition.Channel;
import java.util.Optional;

/**
 * Read-only questions about an entity <em>type</em>, for a module that has to check its own metadata
 * against base-entity's before persisting it.
 *
 * <p>base-state is the caller: a state machine names the attribute holding the current state, and
 * that name has to resolve to a real attribute of a kind that can hold a state key. Requiring it to
 * resolve is also what restricts state machines to entity types base-entity manages.
 *
 * <p>base-workflow's host adapter asks {@link #titleAttribute} to show a workflow instance's subject by
 * name rather than by id.
 *
 * <p>The questions without a {@link Channel} are about the released definition — what the running application
 * uses. One that checks a draft of its own, as base-state does a state machine the designer saves, asks about the
 * entity type's draft, which it will be released with.
 */
public interface EntityAttributeQuery {

    /** {@link #attributeKind(String, String, String)} for either row of the entity type. */
    Optional<EntityAttributeKind> attributeKind(String orgKey, String entityDefinitionCode, String attributeCode, Channel channel);

    /** {@link #entityTypeExists(String, String)} for either row of the entity type. */
    boolean entityTypeExists(String orgKey, String entityDefinitionCode, Channel channel);

    /**
     * The kind of {@code attributeCode} on the entity type {@code entityDefinitionCode} of the
     * organization {@code orgKey} — entity definitions are scoped per organization.
     *
     * <p>Empty for both "no such entity type" and "no such attribute on it" — a caller validating a
     * reference wants the same answer either way, and distinguishing them would only invite a
     * message that leaks which of the two the deployment is missing.
     */
    default Optional<EntityAttributeKind> attributeKind(String orgKey, String entityDefinitionCode, String attributeCode) {
        return attributeKind(orgKey, entityDefinitionCode, attributeCode, Channel.RELEASED);
    }

    /** Whether {@code entityDefinitionCode} resolves to an entity type of {@code orgKey} at all. */
    default boolean entityTypeExists(String orgKey, String entityDefinitionCode) {
        return entityTypeExists(orgKey, entityDefinitionCode, Channel.RELEASED);
    }

    /**
     * The attribute that names an object of {@code entityDefinitionCode} — the one its definition marks
     * {@code isLinkToDetails}, which the generated list renders as the row's link. Empty when the type is
     * unknown or marks none.
     */
    Optional<String> titleAttribute(String orgKey, String entityDefinitionCode);
}
