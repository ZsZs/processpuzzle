package com.processpuzzle.calculation.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entity object as a calculation reads it: id, optimistic-lock version and payload.
 *
 * @param version what a single-object output writes against, so a run never lands on an object changed since it
 *                was read
 */
public record EntityRecord(String id, long version, Map<String, Object> payload) {

    public EntityRecord {
        // Not Map.copyOf: a payload legitimately holds null values.
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
