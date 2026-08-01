package com.processpuzzle.app.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 * @param id unique within its page or region
 * @param type widget registry key, e.g. {@code entity-grid}
 * @param props widget-specific configuration, passed through untouched
 * @param children nested widgets for container widget types; empty for leaf widgets
 */
public record Widget(
        String id,
        String type,
        Map<String, Object> props,
        List<Widget> children) {

    public Widget {
        props = props == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(props));
        children = children == null ? List.of() : List.copyOf(children);
    }
}
