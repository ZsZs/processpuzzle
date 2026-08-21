package com.processpuzzle.state.usecase;

import java.util.List;

/**
 * The outcome of one {@code FireStateTransition} attempt, before the version the object ends up
 * at is known — {@code FireStateTransition} attaches that once {@code
 * EntityObjectGateway.updateStateAttribute} returns, since only the gateway knows the post-write
 * version.
 *
 * <p>A business rejection ({@code success = false}) is a normal outcome, not an exception — see
 * base-state-api.yaml's note on 200-vs-409. It is distinct from {@code
 * StaleEntityObjectVersionException}, a genuine optimistic-lock conflict that {@code
 * FireStateTransition} lets propagate as an exception instead.
 *
 * @param success          whether the transition fired
 * @param previousStateKey the state the object was in when the attempt was made
 * @param newStateKey      the state the object moved to; {@code null} when {@code success} is
 *                         {@code false}
 * @param transitionKey    the {@code Transition} that matched, even when a guard subsequently
 *                         rejected it; {@code null} only when {@code triggerKey} matched no
 *                         transition from the current state at all
 * @param executedActions  {@code beanName}s of the actions that ran, in execution order; empty
 *                         when {@code success} is {@code false}
 * @param rejectionReason  populated when {@code success} is {@code false}
 */
public record TransitionOutcome(
        boolean success,
        String previousStateKey,
        String newStateKey,
        String transitionKey,
        List<String> executedActions,
        String rejectionReason
) {

    public static TransitionOutcome success(String previousStateKey, String newStateKey,
                                             String transitionKey, List<String> executedActions) {
        return new TransitionOutcome(true, previousStateKey, newStateKey, transitionKey, executedActions, null);
    }

    public static TransitionOutcome rejected(String previousStateKey, String transitionKey, String reason) {
        return new TransitionOutcome(false, previousStateKey, null, transitionKey, List.of(), reason);
    }
}
