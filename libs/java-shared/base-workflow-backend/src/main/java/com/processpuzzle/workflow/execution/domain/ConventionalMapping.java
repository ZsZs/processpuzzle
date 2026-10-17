package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.shared.event.OccurredParameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mappings by convention (docs/event-parameters.md, phase 3): a TRIGGERING_EVENT start event or a catch takes every
 * parameter its occurrence carried into the context under the parameter's own name, as if its {@code payloadMapping}
 * said {@code orderNumber: $.payload.orderNumber}. The payload mapping is the override — a name it maps, to whatever,
 * is left to it.
 *
 * <p>Not {@code subject}: every SYSTEM occurrence carries one, so as a plain name it would mean whichever event came
 * last. The subject's plain name is the object name of the artifact it binds.
 */
public final class ConventionalMapping {

    private ConventionalMapping() {}

    /** @return what the payload mapping read, and each carried parameter it does not name, by that name */
    public static Map<String, Object> apply(Map<String, Object> mapped, List<OccurredParameter> parameters) {
        Map<String, Object> context = new LinkedHashMap<>(mapped == null ? Map.of() : mapped);
        for (OccurredParameter parameter : parameters == null ? List.<OccurredParameter>of() : parameters) {
            // containsKey, not putIfAbsent: a name the mapping read as null is still the mapping's.
            if (!OccurredParameter.SUBJECT.equals(parameter.name()) && parameter.value() != null
                    && !context.containsKey(parameter.name())) {
                context.put(parameter.name(), parameter.value());
            }
        }
        return context;
    }
}
