package com.processpuzzle.baseentity.api;

import java.util.Map;
import java.util.UUID;

/**
 * One {@code EntityObject} as {@link EntityObjectAccess} hands it out: identity, optimistic-lock
 * version and full payload. A record rather than the JPA aggregate, so a caller cannot navigate out
 * of it or mutate a managed entity it does not own.
 *
 * <p>The whole payload, not the one attribute the caller asked about: base-state's transition guards
 * may read any attribute of the object, not just the state one.
 *
 * @param id      the object's id
 * @param version the object's current optimistic-lock version
 * @param payload the full attribute payload, keyed by attribute code
 */
public record EntityObjectView(UUID id, long version, Map<String, Object> payload) {

    public EntityObjectView {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
