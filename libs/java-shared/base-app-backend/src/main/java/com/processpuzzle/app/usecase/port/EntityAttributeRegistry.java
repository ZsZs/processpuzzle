package com.processpuzzle.app.usecase.port;

import java.util.Optional;

/**
 * Outbound port answering whether an entity type has an attribute, used to check a route's {@code filterByContext}: the
 * list it narrows filters on the attribute named after the context, so that attribute has to exist.
 *
 * <p>The answer is empty when it is not known — the entity type does not exist, or no implementation is wired — and the
 * validator then makes no claim either way. Nothing is rejected on an answer that is not there.
 */
public interface EntityAttributeRegistry {

    /** Whether {@code entityName} has an attribute named {@code attributeCode}, when that can be answered. */
    default Optional<Boolean> hasAttribute(String orgKey, String entityName, String attributeCode) {
        return Optional.empty();
    }
}
