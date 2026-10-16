package com.processpuzzle.shared.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One parameter of an event occurrence, resolved: its declared name and type, and the value the occurrence
 * carried — null when it carried none. Self-describing, so that a feature reacting to the occurrence needs
 * nothing of the event catalog to tell a record reference from a plain value.
 *
 * @param name       the parameter's name; {@link #SUBJECT} for a SYSTEM event's implicit subject
 * @param type       its type
 * @param entityType {@link EventParameterType#ENTITY_REF} only — the referenced record's type
 * @param value      the value, Jackson-serialisable like the rest of the occurrence
 */
public record OccurredParameter(String name, EventParameterType type, String entityType, Object value) {

    /** The implicit parameter every SYSTEM occurrence carries: the record the platform fact is about. */
    public static final String SUBJECT = "subject";

    /** Whether this is a reference to a record that is actually there. Not a property of the occurrence. */
    @JsonIgnore
    public boolean isReference() {
        return type == EventParameterType.ENTITY_REF && value != null && !String.valueOf(value).isBlank();
    }
}
