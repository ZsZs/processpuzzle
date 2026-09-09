package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.shared.model.ErrorResponse;
import com.processpuzzle.state.usecase.exception.DiagramDefinitionNotFoundException;
import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class StateApiExceptionHandlerTest {

    private StateApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StateApiExceptionHandler();
    }

    @Test
    void handleNotFound_shouldReturn404() {
        StateMachineNotFoundException ex = new StateMachineNotFoundException("org1", "invoice");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("state-machine.not-found");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleDiagramNotFound_shouldReturn404() {
        DiagramDefinitionNotFoundException ex = new DiagramDefinitionNotFoundException("org1", "invoice");
        ResponseEntity<ErrorResponse> response = handler.handleDiagramNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("diagram-definition.not-found");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleAlreadyExists_shouldReturn409() {
        StateMachineAlreadyExistsException ex = new StateMachineAlreadyExistsException("org1", "invoice");
        ResponseEntity<ErrorResponse> response = handler.handleAlreadyExists(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("state-machine.already-exists");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleObjectNotFound_shouldReturn404() {
        EntityObjectNotFoundException ex = new EntityObjectNotFoundException("org1", "invoice", UUID.randomUUID());
        ResponseEntity<ErrorResponse> response = handler.handleObjectNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("entity-object.not-found");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleStaleVersion_shouldReturn409() {
        StaleEntityObjectVersionException ex = new StaleEntityObjectVersionException(UUID.randomUUID(), 1L);
        ResponseEntity<ErrorResponse> response = handler.handleStaleVersion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("entity-object.stale-version");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleUnknownTrigger_shouldReturn400() {
        UnknownTriggerException ex = new UnknownTriggerException("invoice", "cancel");
        ResponseEntity<ErrorResponse> response = handler.handleUnknownTrigger(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("state-machine.transition.unknown-trigger");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }
}
