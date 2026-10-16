package com.processpuzzle.shared.event;

/**
 * The type of a value an event occurrence carries — base-event-api's {@code EventParameterType}.
 * {@link #ENTITY_REF} is the id of one record of the parameter's entity type, which a workflow run binds its
 * artifact of that type to (docs/event-parameters.md).
 */
public enum EventParameterType {
    STRING, NUMBER, BOOLEAN, DATE, ENTITY_REF
}
