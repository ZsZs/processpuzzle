package com.processpuzzle.state.usecase;

/**
 * One entry of {@code GetEntityObjectState}'s dry run — a UI renders this as an enabled or
 * disabled "Approve"-style button without firing anything. {@code guardsSatisfied} can go stale
 * the moment another change lands; {@code FireStateTransition} re-evaluates guards itself and is
 * the only call that is authoritative.
 *
 * @param transitionKey    the {@code Transition.key} this projects
 * @param triggerKey       the verb a caller would invoke to attempt it
 * @param targetStateKey   where it would lead
 * @param guardsSatisfied  whether every guard currently passes against the object's payload
 * @param blockedReason    the first failing guard's message, when {@code guardsSatisfied} is
 *                         {@code false}; otherwise {@code null}
 */
public record AvailableTransitionProjection(
        String transitionKey,
        String triggerKey,
        String targetStateKey,
        boolean guardsSatisfied,
        String blockedReason
) {
}
