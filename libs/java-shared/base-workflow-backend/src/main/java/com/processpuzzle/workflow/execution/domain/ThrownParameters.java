package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills a thrown event's declared parameters from the throwing run's context, by name (docs/event-parameters.md):
 *
 * <ol>
 *   <li>what the throw's payload mapping already put under that name — the explicit override;</li>
 *   <li>otherwise the plain context variable of that name — a payload mapping's or a step's output, an object's
 *   name;</li>
 *   <li>otherwise the one parameter of that name an earlier event carried, namespaced under that event's id
 *   ({@code invoice-issued.invoiceNumber}). Two events carrying it make the name ambiguous, and it is left unfilled:
 *   the throw's payload mapping has to say which.</li>
 * </ol>
 *
 * <p>A parameter nothing fills is left out rather than sent as null.
 */
public final class ThrownParameters {

    private ThrownParameters() {}

    public static Map<String, Object> fill(List<CatalogParameter> declared, Map<String, Object> payload, Map<String, Object> context) {
        Map<String, Object> filled = new LinkedHashMap<>();
        for (CatalogParameter parameter : declared == null ? List.<CatalogParameter>of() : declared) {
            Object value = valueOf(parameter.name(), payload, context);
            if (value != null) {
                filled.put(parameter.name(), value);
            }
        }
        return filled;
    }

    private static Object valueOf(String name, Map<String, Object> payload, Map<String, Object> context) {
        if (payload != null && payload.get(name) != null) {
            return payload.get(name);
        }
        if (context == null) {
            return null;
        }
        if (context.get(name) != null && !(context.get(name) instanceof Map<?, ?>)) {
            return context.get(name);
        }
        List<Object> namespaced = context.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(values -> values.get(name))
                .filter(value -> value != null)
                .map(Object.class::cast)
                .toList();
        return namespaced.size() == 1 ? namespaced.getFirst() : null;
    }
}
