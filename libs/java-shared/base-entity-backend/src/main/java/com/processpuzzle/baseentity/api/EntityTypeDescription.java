package com.processpuzzle.baseentity.api;

import java.util.List;

/**
 * An entity type as a person or a model would read it: its code, its name and the attributes an object has. Read-only
 * and free of base-entity's own types, for a module that has to tell a user or an AI what a form or a rule can refer to.
 *
 * @param code       the definition's code, which linked and embedded attributes name it by
 * @param name       the definition's display name, which a rule's context names it by; the code when it has none
 * @param embedded   whether it is a component that only exists inside another type's objects
 * @param attributes the attributes in the order the definition declares them
 */
public record EntityTypeDescription(String code, String name, boolean embedded, List<AttributeDescription> attributes) {

    public EntityTypeDescription {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** A type that stands on its own. */
    public EntityTypeDescription(String code, String name, List<AttributeDescription> attributes) {
        this(code, name, false, attributes);
    }

    /**
     * One attribute of an entity type.
     *
     * @param code              the attribute's code, which is how an object's data and a rule's {@code entity.code} name it
     * @param kind              what the attribute holds
     * @param required          whether an object must have a value
     * @param multiValued       whether the value is a list
     * @param embedded          whether the value is a component stored inside the object, rather than a link to another
     * @param linkedEntityType  the code of the entity type the value is, or links to; null for a plain value
     * @param measure           {@code QUANTITY}, {@code MONEY} or {@code DIMENSIONS} for a measured value, else null
     * @param measureOptions    the units (or currencies) a measured value may use, or its default one; empty when unknown
     */
    public record AttributeDescription(String code, EntityAttributeKind kind, boolean required, boolean multiValued,
                                       boolean embedded, String linkedEntityType, String measure,
                                       List<String> measureOptions) {

        public AttributeDescription {
            measureOptions = measureOptions == null ? List.of() : List.copyOf(measureOptions);
        }
    }
}
