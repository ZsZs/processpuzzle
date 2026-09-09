package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.domain.event.EntityObjectStateChangedEvent;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import com.processpuzzle.state.usecase.service.StateMachineEngine;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single legitimate way an {@code EntityObject}'s state attribute ever changes — see
 * base-state-api.yaml's "Ownership and the single entry point" note. Resolves {@code
 * (currentStateKey, triggerKey)} via {@link StateMachineEngine}, and only on a successful outcome
 * writes the new state through {@link com.processpuzzle.state.usecase.port.EntityObjectGateway}
 * and publishes {@link EntityObjectStateChangedEvent} — the engine itself never writes anything,
 * keeping the compare-and-swap write (and the optimistic-lock check that guards it) here in the
 * use case.
 */
@Service
@Transactional
public class FireStateTransition {

    private final StateMachineDefinitionRepository repository;
    private final EntityObjectGatewayResolver gatewayResolver;
    private final StateMachineEngine engine;
    private final ApplicationEventPublisher eventPublisher;

    public FireStateTransition(StateMachineDefinitionRepository repository,
                               EntityObjectGatewayResolver gatewayResolver,
                               StateMachineEngine engine,
                               ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.gatewayResolver = gatewayResolver;
        this.engine = engine;
        this.eventPublisher = eventPublisher;
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
            throw new com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException(objectId, expectedVersion);
        }

        Object rawState = snapshot.attribute(definition.getStateAttributeKey());
        String currentStateKey = rawState == null ? definition.getInitialStateKey() : rawState.toString();

        // UnknownTriggerException propagates uncaught here — a structurally invalid request
        // (400), not a business rejection this use case reports as a normal Result.
        TransitionOutcome outcome = engine.fire(
                definition, objectId, currentStateKey, triggerKey, snapshot, requestContext);

        if (!outcome.success()) {
            return new Result(outcome, snapshot.version());
        }

        long newVersion = gateway.updateStateAttribute(
                orgKey, entityName, objectId, definition.getStateAttributeKey(), outcome.newStateKey(), expectedVersion);
        eventPublisher.publishEvent(new EntityObjectStateChangedEvent(
                orgKey, entityName, objectId, outcome.previousStateKey(), outcome.newStateKey(),
                outcome.transitionKey(), triggerKey, newVersion, Instant.now()));
        return new Result(outcome, newVersion);
    }

    /** {@link TransitionOutcome} paired with the object's version after this call. */
    public record Result(TransitionOutcome outcome, long version) {
    }
}
