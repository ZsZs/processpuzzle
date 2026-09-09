package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import com.processpuzzle.state.usecase.exception.DiagramDefinitionNotFoundException;
import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps base-state's own business exceptions to the {@code errorId}/{@code errorText} shape,
 * same contract as {@code RuleApiExceptionHandler}/{@code DocumentApiExceptionHandler}. Ordered
 * ahead of core's generic {@code ApiExceptionHandler} so these take precedence, and after it for
 * anything not listed here — including {@code UnknownGuardBeanException}/{@code
 * UnknownActionBeanException}, deliberately left to core's catch-all as a {@code 500}: a bean
 * disappearing between definition-save-time validation and transition-fire-time is a deployment
 * drift, not a caller mistake.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.state")
@Order(ApiAdviceOrder.FEATURE)
public class StateApiExceptionHandler {

    @ExceptionHandler(StateMachineNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(StateMachineNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "state-machine.not-found", e.getMessage());
    }

    @ExceptionHandler(DiagramDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDiagramNotFound(DiagramDefinitionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "diagram-definition.not-found", e.getMessage());
    }

    @ExceptionHandler(StateMachineAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(StateMachineAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, "state-machine.already-exists", e.getMessage());
    }

    @ExceptionHandler(EntityObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleObjectNotFound(EntityObjectNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "entity-object.not-found", e.getMessage());
    }

    @ExceptionHandler(StaleEntityObjectVersionException.class)
    public ResponseEntity<ErrorResponse> handleStaleVersion(StaleEntityObjectVersionException e) {
        return error(HttpStatus.CONFLICT, "entity-object.stale-version", e.getMessage());
    }

    @ExceptionHandler(UnknownTriggerException.class)
    public ResponseEntity<ErrorResponse> handleUnknownTrigger(UnknownTriggerException e) {
        return error(HttpStatus.BAD_REQUEST, "state-machine.transition.unknown-trigger", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
