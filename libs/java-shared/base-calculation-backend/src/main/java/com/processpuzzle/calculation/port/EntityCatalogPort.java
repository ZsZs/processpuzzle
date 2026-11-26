package com.processpuzzle.calculation.port;

import java.util.List;
import java.util.Optional;

/**
 * Which entity types there are and what attributes they have, as a calculation's source refers to them: by entity
 * definition code. What the authoring assist tells the model a calculation may read and write, and what it checks a
 * candidate against. Names no entity feature; the host application implements it over base-entity.
 */
public interface EntityCatalogPort {

    /** Knows no entity; what a host without an entity catalog gets, so the assist can only check a source's own structure. */
    EntityCatalogPort UNKNOWN = new EntityCatalogPort() {
        @Override
        public List<EntityType> all(String orgKey) {
            return List.of();
        }

        @Override
        public Optional<EntityType> find(String orgKey, String code) {
            return Optional.empty();
        }
    };

    /** Every entity type of the organization, by code. */
    List<EntityType> all(String orgKey);

    /** The entity type with this code, including a type shared from a common that {@link #all} may not list. */
    Optional<EntityType> find(String orgKey, String code);

    /**
     * An entity type.
     *
     * @param code       the entity definition code a source names it by
     * @param name       its display name
     * @param embedded   whether it is a component that only exists inside another type's objects, so it is never queried on its own
     * @param attributes its attributes, in the order the definition declares them
     */
    record EntityType(String code, String name, boolean embedded, List<Attribute> attributes) {

        public EntityType {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }

        public Optional<Attribute> attribute(String name) {
            return attributes.stream().filter(attribute -> attribute.name().equals(name)).findFirst();
        }
    }

    /**
     * An attribute of an entity type.
     *
     * @param name        the attribute's code
     * @param type        what it holds: TEXT, NUMBER, BOOLEAN, DATE, DATE_TIME, ENUM, REFERENCE or PERIOD
     * @param required    whether every object has a value
     * @param multiValued whether the value is a list
     * @param embedded    whether the value is a component stored inside the object
     * @param entity      the code of the entity type it links to or embeds; null for a plain value
     * @param measure     QUANTITY, MONEY or DIMENSIONS for a measured value, else null
     */
    record Attribute(String name, String type, boolean required, boolean multiValued, boolean embedded, String entity,
                     String measure) {
    }
}
