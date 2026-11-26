package com.processpuzzle.rule.port;

import java.util.List;

/**
 * An entity as a rule sees it.
 *
 * @param name       the entity's name, which a rule's {@code context} names it by
 * @param attributes the attributes {@code entity} has, in the order the definition declares them
 */
public record RuleContext(String name, List<ContextAttribute> attributes) {

    public RuleContext {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /**
     * One attribute of an entity, or of a component embedded in it.
     *
     * @param name           the attribute's code, which is how {@code entity.name} reaches it
     * @param type           what it holds: TEXT, NUMBER, BOOLEAN, DATE, DATE_TIME, ENUM, REFERENCE or PERIOD
     * @param required       whether every object has a value
     * @param multiValued    whether the value is a list
     * @param embedded       whether the value is a component stored inside the object; its attributes are {@code children}
     * @param measure        QUANTITY, MONEY or DIMENSIONS for a measured value, else null
     * @param measureOptions the units or currencies a measured value may use, or its default one; empty when unknown
     * @param children       the attributes of an embedded component; empty for anything else, and where the host stopped expanding
     */
    public record ContextAttribute(String name, String type, boolean required, boolean multiValued, boolean embedded,
                                   String measure, List<String> measureOptions, List<ContextAttribute> children) {

        public ContextAttribute {
            measureOptions = measureOptions == null ? List.of() : List.copyOf(measureOptions);
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
