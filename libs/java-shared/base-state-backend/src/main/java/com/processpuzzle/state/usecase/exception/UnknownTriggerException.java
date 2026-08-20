package com.processpuzzle.state.usecase.exception;

/**
 * Thrown by {@code FireStateTransition} when {@code triggerKey} matches no {@code Transition} on
 * the state machine at all — a structurally invalid request, mapped to {@code 400} by {@code
 * StateApiExceptionHandler}. Distinct from the trigger existing but not being available from the
 * object's current state, which is a normal business outcome — see {@code
 * TransitionOutcome.rejectionReason} — not an exception.
 */
public class UnknownTriggerException extends RuntimeException {

    public UnknownTriggerException(String entityName, String triggerKey) {
        super("No transition with triggerKey '" + triggerKey + "' is declared on the state machine for '" + entityName + "'");
    }
}
