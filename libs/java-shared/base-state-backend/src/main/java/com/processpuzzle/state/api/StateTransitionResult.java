package com.processpuzzle.state.api;

/**
 * Result of StateOperationApi.applyTrigger — the in-process mirror of the operation layer's REST
 * response. {@code success=false, conflict=false} is a normal business rejection (200 over REST);
 * {@code conflict=true} is an optimistic-lock clash (409 over REST). {@code newStateKey} is only
 * meaningful when {@code success} is true.
 */
public record StateTransitionResult(boolean success, boolean conflict, String newStateKey, String message) {
}
