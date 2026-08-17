package com.processpuzzle.basestate.adapter.inbound;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.model.EntityObjectStateView;
import com.processpuzzle.basestate.model.StateMachineDefinitionInput;
import com.processpuzzle.basestate.model.TransitionRequest;
import com.processpuzzle.basestate.model.TransitionResult;
import com.processpuzzle.basestate.usecase.CreateStateMachineDefinition;
import com.processpuzzle.basestate.usecase.DeleteStateMachineDefinition;
import com.processpuzzle.basestate.usecase.EntityObjectStateProjection;
import com.processpuzzle.basestate.usecase.ExportStateMachineDefinitions;
import com.processpuzzle.basestate.usecase.FindAllStateMachineDefinitions;
import com.processpuzzle.basestate.usecase.FindStateMachineDefinition;
import com.processpuzzle.basestate.usecase.FireStateTransition;
import com.processpuzzle.basestate.usecase.GetEntityObjectState;
import com.processpuzzle.basestate.usecase.ImportStateMachineDefinitions;
import com.processpuzzle.basestate.usecase.TransitionOutcome;
import com.processpuzzle.basestate.usecase.UpdateStateMachineDefinition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateEndpointTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    private CreateStateMachineDefinition createUseCase;
    private UpdateStateMachineDefinition updateUseCase;
    private DeleteStateMachineDefinition deleteUseCase;
    private FindStateMachineDefinition findUseCase;
    private FindAllStateMachineDefinitions findAllUseCase;
    private ImportStateMachineDefinitions importUseCase;
    private ExportStateMachineDefinitions exportUseCase;
    private GetEntityObjectState getStateUseCase;
    private FireStateTransition fireTransitionUseCase;
    private StateMapper mapper;
    private StateEndpoint endpoint;

    @BeforeEach
    void setUp() {
        createUseCase = mock(CreateStateMachineDefinition.class);
        updateUseCase = mock(UpdateStateMachineDefinition.class);
        deleteUseCase = mock(DeleteStateMachineDefinition.class);
        findUseCase = mock(FindStateMachineDefinition.class);
        findAllUseCase = mock(FindAllStateMachineDefinitions.class);
        importUseCase = mock(ImportStateMachineDefinitions.class);
        exportUseCase = mock(ExportStateMachineDefinitions.class);
        getStateUseCase = mock(GetEntityObjectState.class);
        fireTransitionUseCase = mock(FireStateTransition.class);
        mapper = new StateMapper();

        endpoint = new StateEndpoint(
                createUseCase, updateUseCase, deleteUseCase, findUseCase, findAllUseCase,
                importUseCase, exportUseCase, getStateUseCase, fireTransitionUseCase, mapper);
    }

    @Test
    void createStateMachineDefinition_shouldReturn201() {
        StateMachineDefinitionInput input = new StateMachineDefinitionInput(
                ENTITY, "Invoice Machine", "state", "draft",
                List.of(new com.processpuzzle.basestate.model.State("draft", "Draft")));

        StateMachineDefinition created = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Invoice Machine")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();

        when(createUseCase.execute(any(StateMachineDefinition.class)))
                .thenReturn(created);

        ResponseEntity<com.processpuzzle.basestate.model.StateMachineDefinition> response =
                endpoint.createStateMachineDefinition(ORG, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEntityName()).isEqualTo(ENTITY);
    }

    @Test
    void getEntityObjectState_shouldReturn200() {
        EntityObjectStateProjection projection = new EntityObjectStateProjection(
                OBJECT_ID, ENTITY, "draft", false, null, List.of());
        when(getStateUseCase.execute(ORG, ENTITY, OBJECT_ID)).thenReturn(projection);

        ResponseEntity<EntityObjectStateView> response = endpoint.getEntityObjectState(ORG, ENTITY, OBJECT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCurrentStateKey()).isEqualTo("draft");
    }

    @Test
    void fireStateTransition_shouldReturn200() {
        TransitionRequest request = new TransitionRequest("approve", 1L);
        FireStateTransition.Result result = new FireStateTransition.Result(
                TransitionOutcome.success("draft", "approved", "t1", List.of()), 2L);
        when(fireTransitionUseCase.execute(eq(ORG), eq(ENTITY), eq(OBJECT_ID), eq("approve"), any(), eq(1L)))
                .thenReturn(result);

        ResponseEntity<TransitionResult> response = endpoint.fireStateTransition(ORG, ENTITY, OBJECT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isTrue();
        assertThat(response.getBody().getNewStateKey()).isEqualTo("approved");
        assertThat(response.getBody().getVersion()).isEqualTo(2L);
    }

    @Test
    void deleteStateMachineDefinition_shouldReturn204() {
        ResponseEntity<Void> response = endpoint.deleteStateMachineDefinition(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).execute(ORG, ENTITY);
    }
}
