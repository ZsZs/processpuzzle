package com.processpuzzle.state.usecase.port;

/**
 * A single business condition a {@code Transition} may require. Beans of this type are resolved
 * by name — see {@link com.processpuzzle.state.domain.GuardRef#beanName()} — by {@code
 * GuardActionResolver}, never injected directly, since which guard applies is data (the state
 * machine definition), not wiring.
 *
 * <p>{@code StateMachineEngine} evaluates a transition's guards in declaration order with AND
 * semantics, short-circuiting on the first rejection — so an implementation should be
 * side-effect-free: {@code GetEntityObjectState}'s dry run calls this exactly as {@code
 * FireStateTransition} does, and a guard that mutates anything would fire on every render of a
 * disabled button, not just on an actual attempt.
 */
public interface TransitionGuard {

    GuardResult evaluate(TransitionContext context);
}
