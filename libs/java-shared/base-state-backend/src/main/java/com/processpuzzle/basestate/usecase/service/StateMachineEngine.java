package com.processpuzzle.basestate.usecase.service;

import com.processpuzzle.basestate.domain.ActionRef;
import com.processpuzzle.basestate.domain.GuardRef;
import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.AvailableTransitionProjection;
import com.processpuzzle.basestate.usecase.TransitionOutcome;
import com.processpuzzle.basestate.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.basestate.usecase.port.GuardResult;
import com.processpuzzle.basestate.usecase.port.TransitionAction;
import com.processpuzzle.basestate.usecase.port.TransitionContext;
import com.processpuzzle.basestate.usecase.port.TransitionGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code (currentStateKey, triggerKey) -> Transition} and runs its guards/actions.
 * Shared by {@code GetEntityObjectState} (dry run: guards only, no actions, side-effect-free) and
 * {@code FireStateTransition} (guards then actions, the only call that is authoritative) — see
 * {@link TransitionGuard} for why a guard implementation must be safe to call from both.
 */
@Component
public class StateMachineEngine {

    private final GuardActionResolver guardActionResolver;

    public StateMachineEngine(GuardActionResolver guardActionResolver) {
        this.guardActionResolver = guardActionResolver;
    }

    /**
     * Every transition declared from {@code currentStateKey}, each with its guards evaluated
     * (dry run — no actions run) against {@code snapshot}. See {@code
     * EntityObjectStateProjection.availableTransitions} for the staleness caveat this dry run
     * carries.
     */
    public List<AvailableTransitionProjection> availableTransitions(
            StateMachineDefinition definition, String orgKey, String entityName, UUID objectId,
            String currentStateKey, EntityObjectSnapshot snapshot) {
        List<AvailableTransitionProjection> result = new ArrayList<>();
        for (Transition transition : definition.transitionsFrom(currentStateKey)) {
            GuardOutcome outcome = evaluateGuards(definition, transition, orgKey, entityName, objectId, snapshot, null);
            result.add(new AvailableTransitionProjection(
                    transition.key(), transition.triggerKey(), transition.targetStateKey(),
                    outcome.allowed(), outcome.blockedReason()));
        }
        return result;
    }

    /**
     * Resolves {@code triggerKey} against {@code currentStateKey}, evaluates guards (AND
     * semantics, short-circuiting on the first rejection), and — only if every guard passes —
     * runs actions in declaration order. Does not write anything: {@code FireStateTransition}
     * calls {@code EntityObjectGateway.updateStateAttribute} itself once this returns a
     * successful outcome, keeping the write (and the optimistic-lock check that guards it) in the
     * use case rather than the engine.
     *
     * @throws com.processpuzzle.basestate.usecase.exception.UnknownTriggerException if {@code
     *         triggerKey} matches no transition anywhere on the machine, not just from the current
     *         state — see that exception's javadoc for the distinction from a normal rejection
     */
    public TransitionOutcome fire(StateMachineDefinition definition, String orgKey, String entityName,
                                   UUID objectId, String currentStateKey, String triggerKey,
                                   EntityObjectSnapshot snapshot, Map<String, Object> requestContext) {
        boolean triggerExistsAnywhere = definition.getTransitions().stream()
                .anyMatch(t -> t.triggerKey().equals(triggerKey));
        if (!triggerExistsAnywhere) {
            throw new com.processpuzzle.basestate.usecase.exception.UnknownTriggerException(entityName, triggerKey);
        }

        Transition matching = definition.transitionsFrom(currentStateKey).stream()
                .filter(t -> t.triggerKey().equals(triggerKey))
                .findFirst()
                .orElse(null);
        if (matching == null) {
            return TransitionOutcome.rejected(currentStateKey, null,
                    "No transition for trigger '" + triggerKey + "' from state '" + currentStateKey + "'");
        }

        GuardOutcome guardOutcome = evaluateGuards(definition, matching, orgKey, entityName, objectId, snapshot, requestContext);
        if (!guardOutcome.allowed()) {
            return TransitionOutcome.rejected(currentStateKey, matching.key(), guardOutcome.blockedReason());
        }

        List<String> executed = new ArrayList<>();
        for (ActionRef actionRef : matching.actions()) {
            TransitionAction action = guardActionResolver.resolveAction(actionRef.beanName());
            TransitionContext context = contextFor(
                    definition, matching, orgKey, entityName, objectId, snapshot, requestContext, actionRef.params());
            action.execute(context);
            executed.add(actionRef.beanName());
        }

        return TransitionOutcome.success(currentStateKey, matching.targetStateKey(), matching.key(), executed);
    }

    private GuardOutcome evaluateGuards(StateMachineDefinition definition, Transition transition,
                                         String orgKey, String entityName, UUID objectId,
                                         EntityObjectSnapshot snapshot, Map<String, Object> requestContext) {
        for (GuardRef guardRef : transition.guards()) {
            TransitionGuard guard = guardActionResolver.resolveGuard(guardRef.beanName());
            TransitionContext context = contextFor(
                    definition, transition, orgKey, entityName, objectId, snapshot, requestContext, guardRef.params());
            GuardResult result = guard.evaluate(context);
            if (!result.isAllowed()) {
                return new GuardOutcome(false, result.reason());
            }
        }
        return new GuardOutcome(true, null);
    }

    private TransitionContext contextFor(StateMachineDefinition definition, Transition transition,
                                          String orgKey, String entityName, UUID objectId,
                                          EntityObjectSnapshot snapshot, Map<String, Object> requestContext,
                                          Map<String, Object> guardParams) {
        State source = definition.findState(transition.sourceStateKey()).orElse(null);
        State target = definition.findState(transition.targetStateKey()).orElse(null);
        return new TransitionContext(orgKey, objectId, entityName, source, target, snapshot, guardParams, requestContext);
    }

    private record GuardOutcome(boolean allowed, String blockedReason) {
    }
}
