package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.basestate.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.basestate.usecase.service.EntityObjectGatewayResolver;
import com.processpuzzle.basestate.usecase.service.StateMachineEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The read side of the operation layer: current state plus a guards-only dry run of the
 * transitions available from it. Never writes anything and never runs actions — see {@link
 * StateMachineEngine#availableTransitions} and {@code TransitionGuard}'s side-effect-free
 * contract.
 */
@Service
@Transactional(readOnly = true)
public class GetEntityObjectState {

    private final StateMachineDefinitionRepository repository;
    private final EntityObjectGatewayResolver gatewayResolver;
    private final StateMachineEngine engine;

    public GetEntityObjectState(StateMachineDefinitionRepository repository,
                                EntityObjectGatewayResolver gatewayResolver,
                                StateMachineEngine engine) {
        this.repository = repository;
        this.gatewayResolver = gatewayResolver;
        this.engine = engine;
    }

    public EntityObjectStateProjection execute(String orgKey, String entityName, UUID objectId) {
        StateMachineDefinition definition = repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));

        EntityObjectSnapshot snapshot = gatewayResolver.gateway().findObject(orgKey, entityName, objectId);
        Object rawState = snapshot.attribute(definition.getStateAttributeKey());
        String currentStateKey = rawState == null ? null : rawState.toString();

        boolean knownState = currentStateKey != null && definition.findState(currentStateKey).isPresent();
        boolean isFinal = !knownState || definition.findState(currentStateKey).map(s -> s.isFinal()).orElse(true);
        List<AvailableTransitionProjection> availableTransitions = knownState
                ? engine.availableTransitions(definition, orgKey, entityName, objectId, currentStateKey, snapshot)
                : List.of();

        // enteredStateAt has no source yet: base-state does not persist a transition log in this
        // version, and EntityObjectGateway carries no timestamp for "when this attribute last
        // changed". Left null until one of those exists, rather than fabricated.
        return new EntityObjectStateProjection(objectId, entityName, currentStateKey, isFinal, null, availableTransitions);
    }
}
