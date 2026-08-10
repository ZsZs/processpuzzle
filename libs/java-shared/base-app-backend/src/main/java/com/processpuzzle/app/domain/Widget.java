package com.processpuzzle.app.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reference to a registered frontend widget, as persisted inside {@link AppGraph}. Named
 * {@code Widget} rather than {@code WidgetRef} to avoid a clash with the generated
 * {@code com.processpuzzle.app.model.WidgetRef}.
 *
 * <p>{@code props} is deliberately opaque to the backend: each widget type owns and validates
 * its own props shape on the frontend. Ids referenced from within it — {@code entityName},
 * {@code ruleSetId} — resolve within the enclosing organization.
 *
 * <p>Widgets do not nest. A container widget type (tab group, split panel) lists the ids of
 * sibling widgets in {@code props.childIds}, and those siblings declare
 * {@link WidgetPlacement#REFERENCED} so they are not also rendered at their own position. That
 * keeps the persisted list flat and every widget addressable by id alone.
 *
 * @param id unique within its page or region
 * @param type widget registry key, e.g. {@code entity-grid}
 * @param props widget-specific configuration, passed through untouched
 * @param placement whether the widget renders at its own position or only where it is referenced
 */
public record Widget(
        String id,
        String type,
        Map<String, Object> props,
        WidgetPlacement placement) {

    public Widget {
        props = props == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(props));
        placement = placement == null ? WidgetPlacement.STANDALONE : placement;
    }

    /** Whether this widget renders only where another widget's {@code props.childIds} points at it. */
    public boolean isReferenced() {
        return placement == WidgetPlacement.REFERENCED;
    }
}
