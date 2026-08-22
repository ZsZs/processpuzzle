package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.domain.event.EntityObjectStateChangedEvent;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartStateMachineTest {

    private static final String ORG = "acme";
    private static final String ENTITY = "order";
    private static final String ATTR = "status";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    @Mock
    private StateMachineDefinitionRepository repository;
    @Mock
    private EntityObjectGatewayResolver gatewayResolver;
    @Mock
    private EntityObjectGateway gateway;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StartStateMachine startStateMachine;

    @BeforeEach
    void setUp() {
        startStateMachine = new StartStateMachine(repository, gatewayResolver, eventPublisher);
    }

    private StateMachineDefinition definition() {
        return StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Order SM")
                .stateAttributeKey(ATTR)
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();
    }

    @Test
    void writesTheInitialStateAndPublishesAStartEvent() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, ATTR, "draft", 0L)).thenReturn(1L);

        Optional<String> written = startStateMachine.execute(ORG, ENTITY, OBJECT_ID, Map.of("total", 12), 0L);

        assertThat(written).contains("draft");

        ArgumentCaptor<EntityObjectStateChangedEvent> captor =
                ArgumentCaptor.forClass(EntityObjectStateChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        EntityObjectStateChangedEvent event = captor.getValue();
        assertThat(event.orgKey()).isEqualTo(ORG);
        assertThat(event.entityName()).isEqualTo(ENTITY);
        assertThat(event.objectId()).isEqualTo(OBJECT_ID);
        assertThat(event.newStateKey()).isEqualTo("draft");
        assertThat(event.version()).isEqualTo(1L);
        assertThat(event.occurredAt()).isNotNull();
    }

    /**
     * A null {@code previousStateKey} — and no transition or trigger — is what tells a consumer the
     * machine started rather than advanced. There was no transition, so naming one would be a lie.
     */
    @Test
    void theStartEventCarriesNoPreviousStateTransitionOrTrigger() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.updateStateAttribute(anyString(), anyString(), eq(OBJECT_ID), anyString(), anyString(), eq(0L)))
                .thenReturn(1L);

        startStateMachine.execute(ORG, ENTITY, OBJECT_ID, Map.of(), 0L);

        ArgumentCaptor<EntityObjectStateChangedEvent> captor =
                ArgumentCaptor.forClass(EntityObjectStateChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().previousStateKey()).isNull();
        assertThat(captor.getValue().transitionKey()).isNull();
        assertThat(captor.getValue().triggerKey()).isNull();
    }

    @Test
    void doesNothingWhenNoMachineGovernsTheEntityType() {
        when(repository.findByOrgKeyAndEntityName(ORG, "partner")).thenReturn(Optional.empty());

        assertThat(startStateMachine.execute(ORG, "partner", OBJECT_ID, Map.of(), 0L)).isEmpty();

        verifyNoInteractions(gatewayResolver, eventPublisher);
    }

    @Test
    void leavesAStateTheCreatingPayloadAlreadySet() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition()));

        Optional<String> written =
                startStateMachine.execute(ORG, ENTITY, OBJECT_ID, Map.of(ATTR, "CONFIRMED"), 0L);

        assertThat(written).isEmpty();
        verifyNoInteractions(gatewayResolver, eventPublisher);
    }

    /**
     * A blank string is what an untouched form control sends, so it has to mean "unset" — treating
     * it as a caller-stated state would leave the object permanently outside its machine.
     */
    @Test
    void treatsABlankOrAbsentStateAsUnset() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, ATTR, "draft", 0L)).thenReturn(1L);

        Map<String, Object> blank = new HashMap<>();
        blank.put(ATTR, "   ");
        assertThat(startStateMachine.execute(ORG, ENTITY, OBJECT_ID, blank, 0L)).contains("draft");

        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put(ATTR, null);
        assertThat(startStateMachine.execute(ORG, ENTITY, OBJECT_ID, explicitNull, 0L)).contains("draft");
    }

    @Test
    void toleratesANullPayload() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(definition()));
        when(gatewayResolver.gateway()).thenReturn(gateway);
        when(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, ATTR, "draft", 3L)).thenReturn(4L);

        assertThat(startStateMachine.execute(ORG, ENTITY, OBJECT_ID, null, 3L)).contains("draft");
    }
}
