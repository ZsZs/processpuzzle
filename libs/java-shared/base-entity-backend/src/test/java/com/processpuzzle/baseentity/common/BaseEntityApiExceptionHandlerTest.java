package com.processpuzzle.baseentity.common;

import com.processpuzzle.baseentity.common.ValidationException.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityApiExceptionHandlerTest {

    private BaseEntityApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BaseEntityApiExceptionHandler();
    }

    @Test
    void handleNotFound_returns404() {
        NotFoundException ex = new NotFoundException("Not found message");
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("detail", "Not found message");
    }

    @Test
    void handleConflict_returns409() {
        ConflictException ex = new ConflictException("Conflict message");
        ResponseEntity<Map<String, Object>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        assertThat(response.getBody()).containsEntry("detail", "Conflict message");
    }

    @Test
    void handleOptimisticLock_returns409() {
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("entity", "id");
        ResponseEntity<Map<String, Object>> response = handler.handleOptimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
    }

    @Test
    void handleValidation_returns422WithViolations() {
        ValidationException ex = new ValidationException(List.of(
                new Violation("name", "required"),
                new Violation("code", "must not be blank")
        ));
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("status", 422);
        assertThat(response.getBody()).containsKey("violations");
        @SuppressWarnings("unchecked")
        List<Violation> violations = (List<Violation>) response.getBody().get("violations");
        assertThat(violations).hasSize(2);
    }

    @Test
    void handleBadRequest_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("detail", "Invalid argument");
    }
}
