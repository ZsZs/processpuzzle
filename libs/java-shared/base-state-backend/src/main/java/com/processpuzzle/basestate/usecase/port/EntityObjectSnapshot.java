package com.processpuzzle.basestate.usecase.port;

import java.util.Map;
import java.util.UUID;

/**
 * A read-only view of an {@code EntityObject} as {@link EntityObjectGateway} sees it: its
 * identity, current optimistic-lock version, and full JSONB payload (guards may read any
 * attribute, not just the state attribute).
 *
 * @param id      the object's id
 * @param version the object's current optimistic-lock version
 * @param payload the full attribute payload, keyed by attribute name
 */
public record EntityObjectSnapshot(UUID id, long version, Map<String, Object> payload) {

    public EntityObjectSnapshot {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Convenience accessor for the value the state machine's {@code stateAttributeKey} names. */
    public Object attribute(String key) {
        return payload.get(key);
    }
}
