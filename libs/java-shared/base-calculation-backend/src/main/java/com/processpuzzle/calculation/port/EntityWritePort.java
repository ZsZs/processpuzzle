package com.processpuzzle.calculation.port;

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
     * @throws Conflict when the object changed since it was read, or no longer exists
     */
    void updateAttributes(String orgKey, String entity, String id, Map<String, Object> values, long expectedVersion);

    /**
     * Writes {@code values} onto the object of {@code entity} whose attributes equal {@code key}, creating it when
     * there is none — a series result per boat.
     *
     * @return whether an object was created
     */
    boolean upsertByKey(String orgKey, String entity, Map<String, Object> key, Map<String, Object> values);

    /** A write that must not land: the object moved on, or is gone. */
    class Conflict extends RuntimeException {

        public Conflict(String message) {
            super(message);
        }
    }
}
