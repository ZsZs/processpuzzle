package com.processpuzzle.state.domain;

import java.util.List;

/**
 * One edge of a {@link StateMachineDefinition}: from {@code sourceStateKey}, invoking
 * {@code triggerKey}, to {@code targetStateKey} — provided every guard passes.
 *
 * <p>Callers never name a {@code targetStateKey} directly (see {@code FireStateTransition}); they
 * name a {@code triggerKey}, and {@code StateMachineEngine} resolves
 * {@code (currentStateKey, triggerKey) -> Transition} itself. That is why
 * {@code (sourceStateKey, triggerKey)} — not {@code key} — is the pair that must be unique within
 * a state machine; two transitions may share a {@code triggerKey} only if their
 * {@code sourceStateKey} differs, letting one UI button mean different things depending on where
 * the object currently sits.
 *
 * @param key            Unique within the state machine.
 * @param name            Display name; may be {@code null}.
 * @param sourceStateKey Must resolve to a declared {@link State#key()} that is not {@code isFinal}.
 * @param targetStateKey Must resolve to a declared {@link State#key()}.
 * @param triggerKey     The verb callers invoke — e.g. {@code "approve"}.
 * @param guards         Evaluated in order, AND semantics, short-circuiting on the first that
 *                       rejects. Empty means the transition is unconditional.
 * @param actions        Executed in order, only once every guard has passed.
 */
public record Transition(
        String key,
        String name,
        String sourceStateKey,
        String targetStateKey,
        String triggerKey,
        List<GuardRef> guards,
        List<ActionRef> actions
) {

    public Transition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Transition.key must not be blank");
        }
        if (sourceStateKey == null || sourceStateKey.isBlank()) {
            throw new IllegalArgumentException("Transition.sourceStateKey must not be blank");
        }
        if (targetStateKey == null || targetStateKey.isBlank()) {
            throw new IllegalArgumentException("Transition.targetStateKey must not be blank");
        }
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new IllegalArgumentException("Transition.triggerKey must not be blank");
        }
        guards = guards == null ? List.of() : List.copyOf(guards);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
