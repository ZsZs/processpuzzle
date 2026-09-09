package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.state.api.StateOperationApi;
import com.processpuzzle.state.api.StateTransitionResult;
import com.processpuzzle.workflow.execution.domain.TransitionOutcome;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;

/**
 * Real EntityStateGateway, wired only when base-state's StateOperationApi bean is present in the
 * context (see WorkflowStateGatewayConfig). Its only job is translating between base-state's
 * StateTransitionResult and base-workflow's own TransitionOutcome, so neither module's internal
 * vocabulary leaks into the other.
 */
public class BaseStateEntityStateGateway implements EntityStateGateway {

    private final StateOperationApi stateOperationApi;

    public BaseStateEntityStateGateway(StateOperationApi stateOperationApi) {
        this.stateOperationApi = stateOperationApi;
    }

    @Override
    public TransitionOutcome fireTrigger(String orgKey, String entityName, String entityId, String triggerKey) {
        StateTransitionResult result = stateOperationApi.applyTrigger(orgKey, entityName, entityId, triggerKey);

        if (result.conflict()) {
            return new TransitionOutcome.Conflict(result.message());
        }
        if (!result.success()) {
            return new TransitionOutcome.Rejected(result.message());
        }
        return new TransitionOutcome.Applied(result.newStateKey());
    }

    @Override
    public String currentState(String orgKey, String entityName, String entityId) {
        return stateOperationApi.currentStateKey(orgKey, entityName, entityId);
    }
}
