package com.processpuzzle.calculation.port;

import java.util.List;
import java.util.Map;

/**
 * Writes a run's output. Both writes leave every attribute the calculation does not map as it is, run no payload
 * validation and publish no change event — the values are derived, and an event would trigger the calculation
 * that wrote them.
 */
public interface EntityWritePort {

    /**
     * Writes {@code values} onto one object, provided it is still at {@code expectedVersion}.
     *
     * @throws EntityWriteConflict when the object changed since it was read, or no longer exists
     */
    void updateAttributes(String orgKey, String entity, String id, Map<String, Object> values, long expectedVersion);

    /**
     * Writes each row onto the object of {@code entity} whose {@code keyAttributes} equal the row's, creating it when
     * there is none — a series result per boat. The whole result table in one call, so the adapter can match it in
     * one read.
     *
     * @return how many objects were created
     * @throws EntityWriteConflict when the entity is unknown, or a matched object changed meanwhile
     */
    int upsertAllByKey(String orgKey, String entity, List<String> keyAttributes, List<Map<String, Object>> rows);
}
