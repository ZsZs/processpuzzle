package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.basestate.usecase.port.EntityObjectGateway;
import com.processpuzzle.basestate.usecase.port.EntityObjectSnapshot;
import com.processpuzzle.basestate.usecase.service.EntityObjectGatewayResolver;
import com.processpuzzle.basestate.usecase.service.StateMachineEngine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetEntityObjectStateTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    private StateMachineDefinitionRepository repository;
    private EntityObjectGateway gateway;
    private StateMachineEngine engine;
    private GetEntityObjectState usecase;
    private StateMachineDefinition definition;

    @BeforeEach
    void setUp() {
        repository = mock(StateMachineDefinitionRepository.class);
        gateway = mock(EntityObjectGateway.class);
        EntityObjectGatewayResolver gatewayResolver = mock(EntityObjectGatewayResolver.class);
        when(gatewayResolver.gateway()).thenReturn(gateway);
        engine = mock(StateMachineEngine.class);

        usecase = new GetEntityObjectState(repository, gatewayResolver, engine);

        List<State> states = List.of(
                new State("draft", "Draft", null, false, false, null),
                new State("approved", "Approved", null, true, false, null));
        List<Transition> transitions = List.of(
                new Transition("t1", null, "draft", "approved", "approve", List.of(), List.of()));
        definition = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Invoice Machine")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(states)
                .transitions(transitions)
                .build();

        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition));
    }

    @Test
    void execute_shouldReturnInitialStateWhenObjectHasNoStateAttributeYet() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);
        when(engine.availableTransitions(definition, OBJECT_ID, "draft", snapshot))
                .thenReturn(List.of(new AvailableTransitionProjection("t1", "approve", "approved", true, null)));

        EntityObjectStateProjection result = usecase.execute(ORG, ENTITY, OBJECT_ID);

        assertThat(result.currentStateKey()).isEqualTo("draft");
        assertThat(result.isFinal()).isFalse();
        assertThat(result.availableTransitions()).hasSize(1);
        assertThat(result.availableTransitions().get(0).transitionKey()).isEqualTo("t1");
    }

    @Test
    void execute_shouldReturnStoredStateWhenPresent() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 2L, Map.of("state", "approved"));
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);
        when(engine.availableTransitions(definition, OBJECT_ID, "approved", snapshot))
                .thenReturn(List.of());

        EntityObjectStateProjection result = usecase.execute(ORG, ENTITY, OBJECT_ID);

        assertThat(result.currentStateKey()).isEqualTo("approved");
        assertThat(result.isFinal()).isTrue();
        assertThat(result.availableTransitions()).isEmpty();
    }

    @Test
    void execute_shouldThrowWhenDefinitionNotFound() {
        when(repository.findByOrgKeyAndEntityName(ORG, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.execute(ORG, "unknown", OBJECT_ID))
                .isInstanceOf(StateMachineNotFoundException.class);
    }
}
