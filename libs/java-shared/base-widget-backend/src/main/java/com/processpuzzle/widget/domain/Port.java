package com.processpuzzle.widget.domain;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.util.List;

/**
 * A typed, named slot on a widget type — the domain counterpart of the contract's
 * {@code InputPort} / {@code OutputPort}.
 *
 * <p>One record for both directions, rather than two nearly identical ones. The contract separates
 * them because an input has {@code required}, {@code defaultValue} and {@code defaultRsqlFilter}
 * and an output does not; here they are simply null on an output port. Nothing in this module
 * branches on direction — the lists are stored opaquely and returned as given — so a second record
 * would buy only ceremony. {@code WidgetMapper} is where the distinction is re-established.
 *
 * <p>Persisted as JSON inside {@link WidgetDefinition}, so this is plain data with no behaviour.
 */
public record Port(
        String name,
        PortType type,
        Boolean required,
        String description,
        Object defaultValue,
        String entityType,
        AttributeVisibility attributeVisibility,
        String defaultRsqlFilter) {

    /** Mirrors the contract's PortType. Unknown values fall back to {@link PortType#STRING}. */
    public enum PortType {
        @JsonEnumDefaultValue
        STRING,
        NUMBER,
        BOOLEAN,
        DATE,
        OBJECT,
        ARRAY,
        ENTITY_REF,
        ENTITY_COLLECTION
    }

    /** Mirrors the contract's AttributeVisibility. */
    public record AttributeVisibility(Mode mode, List<String> attributes) {
        public enum Mode {
            @JsonEnumDefaultValue
            ALL,
            INCLUDE,
            EXCLUDE
        }
    }

    /** Convenience for the common case of a scalar input with no entity semantics. */
    public static Port of(String name, PortType type) {
        return new Port(name, type, null, null, null, null, null, null);
    }
}
