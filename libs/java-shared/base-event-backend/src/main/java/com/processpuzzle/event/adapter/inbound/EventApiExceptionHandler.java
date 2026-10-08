package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.event.usecase.exception.EventDefinitionAlreadyExistsException;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import com.processpuzzle.event.usecase.exception.StaleEventDefinitionException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps base-event's own exceptions to the {@code errorId}/{@code errorText} shape. Scoped to this
 * module's package and on the {@link ApiAdviceOrder#FEATURE} rung, so it neither shadows nor is
 * shadowed by another feature's advice claiming the same Spring exception.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.event")
@Order(ApiAdviceOrder.FEATURE)
public class EventApiExceptionHandler {

    @ExceptionHandler(EventDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventDefinitionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "event-definition.not-found", e.getMessage());
    }

    @ExceptionHandler(EventDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(EventDefinitionAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, "event-definition.already-exists", e.getMessage());
    }

    @ExceptionHandler({StaleEventDefinitionException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleStale(RuntimeException e) {
        return error(HttpStatus.CONFLICT, "event-definition.stale-write", e.getMessage());
    }

    @ExceptionHandler(InvalidEventDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidEventDefinitionException e) {
        return error(HttpStatus.BAD_REQUEST, "event-definition.invalid", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
