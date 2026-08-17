package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.basestate.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.basestate.usecase.service.EntityObjectGatewayResolver;
import com.processpuzzle.basestate.usecase.service.StateMachineEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * The single legitimate way an {@code EntityObject}'s state attribute ever changes — see
 * base-state-api.yaml's "Ownership and the single entry point" note. Resolves {@code
 * (currentStateKey, triggerKey)} via {@link StateMachineEngine}, and only on a successful outcome
 * writes the new state through {@link com.processpuzzle.basestate.usecase.port.EntityObjectGateway}
 * — the engine itself never writes anything, keeping the compare-and-swap write (and the
 * optimistic-lock check that guards it) here in the use case.
 */
@Service
@Transactional
public class FireStateTransition {

    private final StateMachineDefinitionRepository repository;
    private final EntityObjectGatewayResolver gatewayResolver;
    private final StateMachineEngine engine;

    public FireStateTransition(StateMachineDefinitionRepository repository,
                               EntityObjectGatewayResolver gatewayResolver,
                               StateMachineEngine engine) {
        this.repository = repository;
        this.gatewayResolver = gatewayResolver;
        this.engine = engine;
    }

    public Result execute(String orgKey, String entityName, UUID objectId,
                          String triggerKey, Map<String, Object> requestContext, long expectedVersion) {
        StateMachineDefinition definition = repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));

        var gateway = gatewayResolver.gateway();
        EntityObjectSnapshot snapshot = gateway.findObject(orgKey, entityName, objectId);

        // A stale read, not a concurrent write: the caller's TransitionRequest.version no longer
        // matches what is stored, so this is answered as 409 rather than as a business rejection
        // — see base-state-api.yaml's note distinguishing the two.
        if (snapshot.version() != expectedVersion) {
            throw new com.processpuzzle.basestate.usecase.exception.StaleEntityObjectVersionException(objectId, expectedVersion);
        }

        Object rawState = snapshot.attribute(definition.getStateAttributeKey());
        String currentStateKey = rawState == null ? definition.getInitialStateKey() : rawState.toString();

        // UnknownTriggerException propagates uncaught here — a structurally invalid request
        // (400), not a business rejection this use case reports as a normal Result.
        TransitionOutcome outcome = engine.fire(
                definition, orgKey, entityName, objectId, currentStateKey, triggerKey, snapshot, requestContext);

        if (!outcome.success()) {
            return new Result(outcome, snapshot.version());
        }

        long newVersion = gateway.updateStateAttribute(
                orgKey, entityName, objectId, definition.getStateAttributeKey(), outcome.newStateKey(), expectedVersion);
        return new Result(outcome, newVersion);
    }

    /** {@link TransitionOutcome} paired with the object's version after this call. */
    public record Result(TransitionOutcome outcome, long version) {
    }
}
