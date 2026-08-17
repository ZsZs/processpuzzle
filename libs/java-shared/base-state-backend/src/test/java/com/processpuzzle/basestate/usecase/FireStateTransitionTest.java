package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.domain.event.EntityObjectStateChangedEvent;
import com.processpuzzle.basestate.usecase.exception.StaleEntityObjectVersionException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FireStateTransitionTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    private StateMachineDefinitionRepository repository;
    private EntityObjectGateway gateway;
    private StateMachineEngine engine;
    private ApplicationEventPublisher eventPublisher;
    private FireStateTransition usecase;
    private StateMachineDefinition definition;

    @BeforeEach
    void setUp() {
        repository = mock(StateMachineDefinitionRepository.class);
        gateway = mock(EntityObjectGateway.class);
        EntityObjectGatewayResolver gatewayResolver = mock(EntityObjectGatewayResolver.class);
        when(gatewayResolver.gateway()).thenReturn(gateway);
        engine = mock(StateMachineEngine.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        usecase = new FireStateTransition(repository, gatewayResolver, engine, eventPublisher);

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
    void execute_shouldSucceedAndUpdateStateAndPublishEvent() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of("state", "draft"));
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);
        when(engine.fire(definition, OBJECT_ID, "draft", "approve", snapshot, Map.of()))
                .thenReturn(TransitionOutcome.success("draft", "approved", "t1", List.of()));
        when(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, "state", "approved", 1L))
                .thenReturn(2L);

        FireStateTransition.Result result = usecase.execute(ORG, ENTITY, OBJECT_ID, "approve", Map.of(), 1L);

        assertThat(result.outcome().success()).isTrue();
        assertThat(result.outcome().newStateKey()).isEqualTo("approved");
        assertThat(result.version()).isEqualTo(2L);

        ArgumentCaptor<EntityObjectStateChangedEvent> eventCaptor = ArgumentCaptor.forClass(EntityObjectStateChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EntityObjectStateChangedEvent published = eventCaptor.getValue();
        assertThat(published.orgKey()).isEqualTo(ORG);
        assertThat(published.entityName()).isEqualTo(ENTITY);
        assertThat(published.objectId()).isEqualTo(OBJECT_ID);
        assertThat(published.previousStateKey()).isEqualTo("draft");
        assertThat(published.newStateKey()).isEqualTo("approved");
        assertThat(published.transitionKey()).isEqualTo("t1");
        assertThat(published.triggerKey()).isEqualTo("approve");
        assertThat(published.version()).isEqualTo(2L);
        assertThat(published.occurredAt()).isNotNull();
    }

    @Test
    void execute_shouldDefaultToInitialStateWhenRawStateIsNull() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of());
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);
        when(engine.fire(definition, OBJECT_ID, "draft", "approve", snapshot, null))
                .thenReturn(TransitionOutcome.success("draft", "approved", "t1", List.of()));
        when(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, "state", "approved", 1L))
                .thenReturn(2L);

        FireStateTransition.Result result = usecase.execute(ORG, ENTITY, OBJECT_ID, "approve", null, 1L);

        assertThat(result.outcome().success()).isTrue();
        verify(eventPublisher).publishEvent(any(EntityObjectStateChangedEvent.class));
    }

    @Test
    void execute_shouldThrowWhenVersionMismatch() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 2L, Map.of());
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);

        assertThatThrownBy(() -> usecase.execute(ORG, ENTITY, OBJECT_ID, "approve", null, 1L))
                .isInstanceOf(StaleEntityObjectVersionException.class);

        verify(gateway, never()).updateStateAttribute(any(), any(), any(), any(), any(), any(Long.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void execute_shouldNotUpdateOrPublishWhenOutcomeIsRejected() {
        EntityObjectSnapshot snapshot = new EntityObjectSnapshot(OBJECT_ID, 1L, Map.of("state", "draft"));
        when(gateway.findObject(ORG, ENTITY, OBJECT_ID)).thenReturn(snapshot);
        when(engine.fire(definition, OBJECT_ID, "draft", "approve", snapshot, null))
                .thenReturn(TransitionOutcome.rejected("draft", "t1", "guard failed"));

        FireStateTransition.Result result = usecase.execute(ORG, ENTITY, OBJECT_ID, "approve", null, 1L);

        assertThat(result.outcome().success()).isFalse();
        assertThat(result.outcome().rejectionReason()).isEqualTo("guard failed");
        assertThat(result.version()).isEqualTo(1L);

        verify(gateway, never()).updateStateAttribute(any(), any(), any(), any(), any(), any(Long.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void execute_shouldThrowWhenDefinitionNotFound() {
        when(repository.findByOrgKeyAndEntityName(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.execute(ORG, "missing", OBJECT_ID, "approve", null, 1L))
                .isInstanceOf(StateMachineNotFoundException.class);
    }
}
