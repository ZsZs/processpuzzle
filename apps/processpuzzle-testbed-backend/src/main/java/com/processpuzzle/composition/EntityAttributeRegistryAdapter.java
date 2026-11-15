package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.port.EntityAttributeRegistry;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers base-app whether an entity type has an attribute, from base-entity's definitions. Neither library names the
 * other; see the package documentation.
 *
 * <p>An entity type that does not exist answers empty: whether it exists is the entity-name check's question, and it is
 * not this one's to answer twice.
 */
@Component
public class EntityAttributeRegistryAdapter implements EntityAttributeRegistry {

    private final EntityAttributeQuery attributeQuery;

    public EntityAttributeRegistryAdapter(EntityAttributeQuery attributeQuery) {
        this.attributeQuery = attributeQuery;
    }

    @Override
    public Optional<Boolean> hasAttribute(String orgKey, String entityName, String attributeCode) {
        if (!attributeQuery.entityTypeExists(orgKey, entityName)) {
            return Optional.empty();
        }
        return Optional.of(attributeQuery.attributeKind(orgKey, entityName, attributeCode).isPresent());
    }
}
