package com.processpuzzle.chat.definition.usecases.outbound;

import java.util.List;

/**
 * The states a kind of subject can be in, as far as the host knows them.
 *
 * @param keys the state keys of the subject's state machine; empty for {@link #NONE}
 */
public record SubjectStates(List<String> keys) {

    /** No state machine governs the subject: it has no states a chat could be bound to. */
    public static final SubjectStates NONE = new SubjectStates(List.of());

    public SubjectStates {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    public boolean governed() {
        return !keys.isEmpty();
    }
}
