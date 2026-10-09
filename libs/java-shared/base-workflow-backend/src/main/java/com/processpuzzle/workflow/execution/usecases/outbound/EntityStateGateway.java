package com.processpuzzle.workflow.execution.usecases.outbound;

import com.processpuzzle.workflow.execution.domain.TransitionOutcome;

/**
 * Outbound port from base-workflow to whatever module owns the entity's state machine
 * (base-state, in this deployment). Mirrors base-state's own EntityObjectGateway port
 * towards base-entity: a narrow, workflow-owned interface that a real adapter satisfies
 * when the dependency is present, and a loud fallback satisfies when it is not.
 */
public interface EntityStateGateway {

    /**
     * Fires {@code triggerKey} against the state machine instance bound to
     * {@code entityName}/{@code entityId} within {@code orgKey}.
     */
    TransitionOutcome fireTrigger(String orgKey, String entityName, String entityId, String triggerKey);

    /**
     * Reads the entity's current state key, for Task.preconditionStateKey checks.
     */
    String currentState(String orgKey, String entityName, String entityId);

    /**
     * Whether a state-machine module stands behind this gateway. A check that is only a guard —
     * an INPUT_ARTIFACT start event's required state — permits rather than fails when it does not.
     */
    default boolean isAvailable() {
        return true;
    }
}
