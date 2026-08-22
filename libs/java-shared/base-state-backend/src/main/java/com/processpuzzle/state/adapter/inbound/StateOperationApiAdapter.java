package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.api.StateOperationApi;
import com.processpuzzle.state.api.StateTransitionResult;
import com.processpuzzle.state.usecase.EntityObjectStateProjection;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.GetEntityObjectState;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements {@link StateOperationApi} over the same use cases the REST operation layer calls, so
 * an in-process caller and an HTTP one cannot drift into different behaviour. Until this existed
 * the interface had no implementation at all, which meant base-workflow's {@code
 * WorkflowStateGatewayConfig.getIfAvailable()} always returned null and every task-completion
 * trigger was silently discarded.
 *
 * <p><b>Where the version comes from.</b> {@code FireStateTransition} is a compare-and-swap and
 * needs an expected version; {@link StateOperationApi#applyTrigger} takes none, because its callers
 * are reacting to something that happened rather than submitting a form they read earlier. So the
 * version is read here, immediately before the attempt. That is a genuinely weaker guarantee than
 * the REST path — a write landing in the gap is not detected — and it is the right one for this
 * caller: a workflow step firing a trigger has no earlier read to be stale against. A conflict can
 * still surface if the object changes inside the attempt, and is reported as one.
 */
@Component
class StateOperationApiAdapter implements StateOperationApi {

    private final FireStateTransition fireStateTransition;
    private final GetEntityObjectState getEntityObjectState;
    private final EntityObjectGatewayResolver gatewayResolver;

    StateOperationApiAdapter(FireStateTransition fireStateTransition,
                             GetEntityObjectState getEntityObjectState,
                             EntityObjectGatewayResolver gatewayResolver) {
        this.fireStateTransition = fireStateTransition;
        this.getEntityObjectState = getEntityObjectState;
        this.gatewayResolver = gatewayResolver;
    }

    @Override
    public StateTransitionResult applyTrigger(String orgKey, String entityName, String entityId, String triggerKey) {
        UUID objectId = UUID.fromString(entityId);
        EntityObjectSnapshot snapshot = gatewayResolver.gateway().findObject(orgKey, entityName, objectId);

        try {
            FireStateTransition.Result result = fireStateTransition.execute(
                    orgKey, entityName, objectId, triggerKey, Map.of(), snapshot.version());
            var outcome = result.outcome();
            return new StateTransitionResult(
                    outcome.success(), false, outcome.newStateKey(),
                    outcome.success() ? null : outcome.rejectionReason());
        } catch (StaleEntityObjectVersionException e) {
            return new StateTransitionResult(false, true, null, e.getMessage());
        } catch (UnknownTriggerException e) {
            // Over REST this is a 400 — a caller naming a trigger the machine does not declare. In
            // process there is no status code to carry that, and the distinction between "trigger
            // does not exist" and "trigger does not apply here" is not one this caller can act on
            // differently, so it is reported as an ordinary rejection.
            return new StateTransitionResult(false, false, null, e.getMessage());
        }
    }

    @Override
    public String currentStateKey(String orgKey, String entityName, String entityId) {
        EntityObjectStateProjection projection =
                getEntityObjectState.execute(orgKey, entityName, UUID.fromString(entityId));
        return projection.currentStateKey();
    }
}
