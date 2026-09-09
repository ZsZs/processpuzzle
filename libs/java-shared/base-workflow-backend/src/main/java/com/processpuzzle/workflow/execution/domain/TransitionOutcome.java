package com.processpuzzle.workflow.execution.domain;

/**
 * Result of firing a state-machine trigger, as seen from base-workflow. Mirrors base-state's
 * operation-layer response shape: a business rejection is a normal 200 with success:false, an
 * optimistic-lock clash is a 409. Applied/Rejected/Conflict keeps that distinction visible to
 * the caller instead of collapsing it into a boolean.
 */
public sealed interface TransitionOutcome {

    record Applied(String newStateKey) implements TransitionOutcome {}

    record Rejected(String reason) implements TransitionOutcome {}

    record Conflict(String message) implements TransitionOutcome {}
}
