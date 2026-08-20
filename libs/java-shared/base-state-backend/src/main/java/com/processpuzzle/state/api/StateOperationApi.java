package com.processpuzzle.state.api;

import org.springframework.modulith.NamedInterface;

/**
 * Public surface base-state exposes to other Spring Modulith application modules — base-workflow
 * in particular — for driving and reading an entity's state machine. Everything else in
 * base-state stays package-private to the module, per your existing @ApplicationModule /
 * allowedDependencies convention. This does not replace the operation-layer REST endpoints;
 * it's the in-process equivalent for same-deployment callers, avoiding an HTTP round trip for
 * something that's really just a method call within the monolith.
 */
@NamedInterface("operations")
public interface StateOperationApi {

    /**
     * Applies {@code triggerKey} to the state machine instance bound to
     * {@code entityName}/{@code entityId} within {@code orgKey}. Returns success:false (not an
     * exception) for a business rejection — e.g. no transition for the current state — and sets
     * {@code conflict} for an optimistic-lock clash, exactly matching the REST operation layer.
     */
    StateTransitionResult applyTrigger(String orgKey, String entityName, String entityId, String triggerKey);

    /**
     * Reads the entity's current state key. Throws if no state machine instance exists for the
     * entity — callers (Task.preconditionStateKey checks) should only ask this for entities they
     * know are under a state machine.
     */
    String currentStateKey(String orgKey, String entityName, String entityId);
}
