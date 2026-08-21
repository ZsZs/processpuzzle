package com.processpuzzle.state.usecase.port;

/**
 * A side effect to run once every guard on a {@code Transition} has passed — e.g. sending a
 * notification, or writing an audit entry. Beans of this type are resolved by name — see {@link
 * com.processpuzzle.state.domain.ActionRef#beanName()} — by {@code GuardActionResolver}.
 *
 * <p>{@code StateMachineEngine} runs a transition's actions in declaration order, only inside
 * {@code FireStateTransition} — never during {@code GetEntityObjectState}'s dry run, unlike
 * guards, which both call.
 */
public interface TransitionAction {

    void execute(TransitionContext context);
}
