package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import com.processpuzzle.state.usecase.service.StateMachineEngine;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String currentStateKey = rawState == null ? definition.getInitialStateKey() : rawState.toString();

        boolean knownState = definition.findState(currentStateKey).isPresent();
        boolean isFinal = definition.findState(currentStateKey).map(State::isFinal).orElse(false);
        List<AvailableTransitionProjection> availableTransitions = knownState
                ? engine.availableTransitions(definition, objectId, currentStateKey, snapshot)
                : List.of();

        // enteredStateAt has no source yet: base-state does not persist a transition log in this
        // version, and EntityObjectGateway carries no timestamp for "when this attribute last
        // changed". Left null until one of those exists, rather than fabricated.
        return new EntityObjectStateProjection(objectId, entityName, currentStateKey, isFinal, null, availableTransitions);
    }
}
