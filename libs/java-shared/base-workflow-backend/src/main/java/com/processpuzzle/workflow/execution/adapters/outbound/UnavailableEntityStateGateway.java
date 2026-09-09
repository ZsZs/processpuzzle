package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.workflow.execution.domain.TransitionOutcome;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;

/**
 * Fails loudly rather than silently when base-state isn't present. Mirrors base-state's own
 * UnavailableEntityObjectGateway towards base-entity: tasks with no completionStateTriggerKey /
 * preconditionStateKey are entirely unaffected, so the rest of base-workflow keeps working —
 * only the state-coupled tasks surface a clear error instead of quietly no-opping.
 */
public class UnavailableEntityStateGateway implements EntityStateGateway {

    @Override
    public TransitionOutcome fireTrigger(String orgKey, String entityName, String entityId, String triggerKey) {
        throw new IllegalStateException(
                "No state-machine module is available to fire trigger '%s' on %s/%s (org '%s'). "
                        + "This task declares a completionStateTriggerKey but base-state is not on the classpath."
                        .formatted(triggerKey, entityName, entityId, orgKey));
    }

    @Override
    public String currentState(String orgKey, String entityName, String entityId) {
        throw new IllegalStateException(
                "No state-machine module is available to read the state of %s/%s (org '%s'). "
                        + "This task declares a preconditionStateKey but base-state is not on the classpath."
                        .formatted(entityName, entityId, orgKey));
    }
}
