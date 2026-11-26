package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityTypeDescription;
import com.processpuzzle.baseentity.api.EntityTypeDescription.AttributeDescription;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tells base-calculation which entity types the organization has and what attributes they carry, from base-entity's
 * definitions. A calculation names an entity by its code. {@link #find} also answers for a type that is only a draft, which
 * is what a designer writing a calculation for a type still being designed has to see.
 */
@Component
public class CalculationEntityCatalogAdapter implements EntityCatalogPort {

    private final EntityAttributeQuery entities;

    public CalculationEntityCatalogAdapter(EntityAttributeQuery entities) {
        this.entities = entities;
    }

    @Override
    public List<EntityType> all(String orgKey) {
        return entities.describeAll(orgKey).stream().map(CalculationEntityCatalogAdapter::toType).toList();
    }

    @Override
    public Optional<EntityType> find(String orgKey, String code) {
        return entities.describe(orgKey, code, Channel.RELEASED)
            .or(() -> entities.describe(orgKey, code, Channel.DRAFT))
            .map(CalculationEntityCatalogAdapter::toType);
    }

    private static EntityType toType(EntityTypeDescription type) {
        return new EntityType(type.code(), type.name(), type.embedded(), type.attributes().stream().map(CalculationEntityCatalogAdapter::toAttribute).toList());
    }

    private static Attribute toAttribute(AttributeDescription attribute) {
        return new Attribute(attribute.code(), attribute.kind().name(), attribute.required(), attribute.multiValued(), attribute.embedded(),
            attribute.linkedEntityType(), attribute.measure());
    }
}
