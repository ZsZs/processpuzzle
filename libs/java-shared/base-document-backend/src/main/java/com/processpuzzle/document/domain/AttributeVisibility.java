package com.processpuzzle.document.domain;

import java.io.Serializable;
import java.util.List;

/**
 * Attribute projection applied to entity data carried through an ENTITY_REF or
 * ENTITY_COLLECTION port. A plain allow/deny list — attribute names are not validated
 * against base-entity's schema here, matching {@code EntityBinding}'s own scope boundary.
 *
 * @param mode       ALL (default), INCLUDE, or EXCLUDE
 * @param attributes used when mode is INCLUDE or EXCLUDE; ignored when ALL
 */
public record AttributeVisibility(Mode mode, List<String> attributes) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Mode { ALL, INCLUDE, EXCLUDE }

    public AttributeVisibility {
        mode = mode == null ? Mode.ALL : mode;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public static AttributeVisibility all() {
        return new AttributeVisibility(Mode.ALL, List.of());
    }
}
