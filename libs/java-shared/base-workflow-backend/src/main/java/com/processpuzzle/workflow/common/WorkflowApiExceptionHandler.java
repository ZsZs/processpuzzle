package com.processpuzzle.workflow.common;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Base Workflow exceptions into the {@link ErrorResponse} shape declared by
 * base-workflow-api.yaml. Mirrors base-entity-backend's and base-rule-backend's own advice classes;
 * each feature module owns its own so the {@code errorId}/{@code errorText} vocabulary stays close
 * to the domain that raised it.
 */
@RestControllerAdvice
@Order(ApiAdviceOrder.FEATURE)
public class WorkflowApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "workflow.notFound", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "workflow.conflict", ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "workflow.validation", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "workflow.versionConflict",
                "The resource was modified concurrently — reload and retry.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "workflow.badRequest", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> problem(HttpStatus status, String errorId, String detail) {
        return ResponseEntity.status(status).body(new ErrorResponse().errorId(errorId).errorText(detail));
    }
}
