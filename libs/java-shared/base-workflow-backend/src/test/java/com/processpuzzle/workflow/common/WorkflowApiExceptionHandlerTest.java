package com.processpuzzle.workflow.common;

import com.processpuzzle.shared.model.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApiExceptionHandlerTest {

    private WorkflowApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkflowApiExceptionHandler();
    }

    @Test
    void handleNotFound_shouldReturn404() {
        NotFoundException ex = new NotFoundException("Not found message");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("workflow.notFound");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleConflict_shouldReturn409() {
        ConflictException ex = new ConflictException("Conflict message");
        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("workflow.conflict");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleValidation_shouldReturn400() {
        ValidationException ex = new ValidationException("Validation error");
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("workflow.validation");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }

    @Test
    void handleOptimisticLock_shouldReturn409() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("entity", 1L);
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("workflow.versionConflict");
        assertThat(response.getBody().getErrorText()).isEqualTo("The resource was modified concurrently — reload and retry.");
    }

    @Test
    void handleBadRequest_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isEqualTo("workflow.badRequest");
        assertThat(response.getBody().getErrorText()).isEqualTo(ex.getMessage());
    }
}
