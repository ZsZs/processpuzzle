package com.processpuzzle.rule.usecase.engine;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only view over a {@code Map<String, Object>} (and nested maps/lists within it)
 * exposed to PPCL rule expressions running inside a sandboxed GraalJS context.
 *
 * <p>Because the GraalJS Context is built with {@code allowHostAccess(HostAccess.NONE)},
 * rule expressions have no way to reach real Java objects, methods, or classes. The only
 * thing a rule can see is whatever is explicitly wrapped and handed to it here — and even
 * that is read-only, since {@link #putMember} always throws.
 *
 * <p>Entity data is expected as a plain nested structure of {@code Map<String,Object>},
 * {@code List<Object>}, and JS-primitive-compatible leaf values (String, Number, Boolean,
 * null). In a real ProcessPuzzle integration this map would typically be produced by a
 * small adapter that walks your metadata-driven entity (e.g. via the field registry) rather
 * than reflecting over the Java object directly — keeping the "what a rule can see" surface
 * explicit and auditable.
 */
public final class EntityProxy implements ProxyObject {
    private final Map<String, Object> fields;
    private EntityProxy(Map<String, Object> fields) {
        this.fields = fields;
    }

    /**
     * Wraps a Map as the root object passed into a rule's {@code entity} parameter.
     */
    public static EntityProxy wrap(Map<String, Object> fields) {
        return new EntityProxy(fields);
    }

    /**
     * Recursively wraps a value for exposure to JS: maps become EntityProxy objects,
     * lists become ProxyArray, everything else passes through unchanged (GraalJS converts
     * Java String/Number/Boolean/null to native JS primitives automatically).
     */
    @SuppressWarnings("unchecked")
    private static Object wrapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new EntityProxy((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            List<Object> wrapped = new ArrayList<>(list.size());
            for (Object item : list) {
                wrapped.add(wrapValue(item));
            }
            return ProxyArray.fromList(wrapped);
        }
        return value;
    }

    @Override
    public Object getMember(String key) {
        return wrapValue(fields.get(key));
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<>(fields.keySet()));
    }

    @Override
    public boolean hasMember(String key) {
        return fields.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException(
                "Rule expressions are read-only; '" + key + "' cannot be modified from within a rule.");
    }
}
