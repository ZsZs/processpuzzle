package com.processpuzzle.basestate.adapter.inbound;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.model.EntityObjectStateView;
import com.processpuzzle.basestate.model.PageOfStateMachineDefinition;
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
import com.processpuzzle.basestate.usecase.ImportOutcome;
import com.processpuzzle.basestate.usecase.ImportStateMachineDefinitions;
import com.processpuzzle.basestate.usecase.TransitionOutcome;
import com.processpuzzle.basestate.usecase.UpdateStateMachineDefinition;
import com.processpuzzle.shared.model.ImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void getStateMachineDefinition_shouldReturn200() {
        StateMachineDefinition definition = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Invoice Machine")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();

        when(findUseCase.execute(ORG, ENTITY)).thenReturn(definition);

        ResponseEntity<com.processpuzzle.basestate.model.StateMachineDefinition> response =
                endpoint.getStateMachineDefinition(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEntityName()).isEqualTo(ENTITY);
    }

    @Test
    void updateStateMachineDefinition_shouldReturn200() {
        StateMachineDefinitionInput input = new StateMachineDefinitionInput(
                ENTITY, "Invoice Machine Updated", "state", "draft",
                List.of(new com.processpuzzle.basestate.model.State("draft", "Draft")));

        StateMachineDefinition updated = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Invoice Machine Updated")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();

        when(updateUseCase.execute(eq(ORG), eq(ENTITY), any(StateMachineDefinition.class)))
                .thenReturn(updated);

        ResponseEntity<com.processpuzzle.basestate.model.StateMachineDefinition> response =
                endpoint.updateStateMachineDefinition(ORG, ENTITY, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Invoice Machine Updated");
    }

    @Test
    void deleteStateMachineDefinition_shouldReturn204() {
        ResponseEntity<Void> response = endpoint.deleteStateMachineDefinition(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).execute(ORG, ENTITY);
    }

    @Test
    void listStateMachineDefinitions_shouldReturn200() {
        StateMachineDefinition def = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name("Invoice Machine")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(List.of(new State("draft", "Draft", null, false, false, null)))
                .transitions(List.of())
                .build();

        when(findAllUseCase.execute(ORG, "name=='Invoice Machine'", "name,asc", 0, 10))
                .thenReturn(new PageImpl<>(List.of(def), PageRequest.of(0, 10), 1));

        ResponseEntity<PageOfStateMachineDefinition> response =
                endpoint.listStateMachineDefinitions(ORG, "name=='Invoice Machine'", "name,asc", 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    void exportStateMachineDefinitions_shouldReturnYamlBytes() throws IOException {
        byte[] yamlBytes = "stateMachines: []".getBytes();
        when(exportUseCase.execute(ORG, ENTITY)).thenReturn(yamlBytes);

        ResponseEntity<Resource> response = endpoint.exportStateMachineDefinitions(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo(yamlBytes);
    }

    @Test
    void exportStateMachineDefinitions_throwsUncheckedIOExceptionOnError() throws IOException {
        when(exportUseCase.execute(ORG, ENTITY)).thenThrow(new IOException("Disk error"));

        assertThatThrownBy(() -> endpoint.exportStateMachineDefinitions(ORG, ENTITY))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void importStateMachineDefinitions_shouldReturn200() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.yaml", "text/yaml", "dummy".getBytes());
        ImportOutcome outcome = new ImportOutcome(2, 1, List.of());
        when(importUseCase.execute(eq(ORG), any(InputStream.class))).thenReturn(outcome);

        ResponseEntity<ImportResult> response = endpoint.importStateMachineDefinitions(ORG, file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCreated()).isEqualTo(2);
        assertThat(response.getBody().getUpdated()).isEqualTo(1);
        assertThat(response.getBody().getErrors()).isEmpty();
    }

    @Test
    void importStateMachineDefinitions_throwsUncheckedIOExceptionOnError() throws IOException {
        MockMultipartFile file = mock(MockMultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("Read error"));

        assertThatThrownBy(() -> endpoint.importStateMachineDefinitions(ORG, file))
                .isInstanceOf(UncheckedIOException.class);
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
}
