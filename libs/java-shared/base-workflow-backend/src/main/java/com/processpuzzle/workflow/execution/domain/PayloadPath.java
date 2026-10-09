package com.processpuzzle.workflow.execution.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSONPath subset a start event's {@code payloadMapping} may use: {@code $} for the whole event,
 * {@code $.a.b} for a nested property, and a numeric segment ({@code $.items.0}) for a list element.
 * No filters, wildcards or bracket notation — a mapping picks values out, it does not query — which is
 * what keeps a JSONPath runtime out of this module.
 */
public final class PayloadPath {

    private PayloadPath() {
    }

    /**
     * @return the value at {@code path}, or null when the path does not resolve — a missing property
     *         is an empty context variable, not a failed start
     * @throws IllegalArgumentException if {@code path} is not of the supported form
     */
    public static Object resolve(Object root, String path) {
        if (path == null || !(path.equals("$") || path.startsWith("$."))) {
            throw new IllegalArgumentException("Unsupported payload path '%s' — expected '$' or '$.a.b'".formatted(path));
        }
        Object current = root;
        if (path.equals("$")) {
            return current;
        }
        for (String segment : path.substring(2).split("\\.", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Unsupported payload path '%s' — empty segment".formatted(path));
            }
            current = step(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Applies every mapping to {@code root}; keys are context variable names. */
    public static Map<String, Object> map(Object root, Map<String, String> mapping) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (mapping != null) {
            mapping.forEach((variable, path) -> context.put(variable, resolve(root, path)));
        }
        return context;
    }

    private static Object step(Object current, String segment) {
        if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        if (current instanceof List<?> list && segment.chars().allMatch(Character::isDigit)) {
            int index = Integer.parseInt(segment);
            return index < list.size() ? list.get(index) : null;
        }
        return null;
    }
}
