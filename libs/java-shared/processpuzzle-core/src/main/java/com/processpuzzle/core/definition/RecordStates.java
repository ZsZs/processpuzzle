package com.processpuzzle.core.definition;

import java.util.Map;
import java.util.Optional;

/**
 * Where the state of a record is kept, for the features that need to know a record's state without knowing state
 * machines: base-state implements it, a host without base-state has no state to read.
 *
 * <p>A record's state is a value of one of its own attributes, which the type's released state machine names; a record
 * that does not hold the value yet is in the machine's initial state. See {@code docs/shared-data-commons-concept}
 * ({@code visibleWhen}).
 */
public interface RecordStates {

    /** The state tracking of the type {@code entityDefinitionCode} in {@code orgKey}; empty for a type without a state machine. */
    Optional<StateTracking> trackingOf(String orgKey, String entityDefinitionCode);

    /**
     * @param attributeKey  the attribute of the record that holds its state
     * @param initialState  the state of a record that holds none yet
     */
    record StateTracking(String attributeKey, String initialState) {

        /** The state the record with {@code payload} is in. */
        public String stateIn(Map<String, Object> payload) {
            Object held = payload == null ? null : payload.get(attributeKey);
            return held == null ? initialState : held.toString();
        }
    }
}
