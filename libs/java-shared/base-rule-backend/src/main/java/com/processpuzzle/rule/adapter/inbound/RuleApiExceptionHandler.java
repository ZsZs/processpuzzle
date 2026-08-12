package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Module-specific exceptions only; the generic ones belong to core's {@code ApiExceptionHandler}.
 * Bodies are the {@code ErrorResponse} of base-rule-api.yaml — {@code errorId} plus {@code errorText}.
 */
@RestControllerAdvice
public class RuleApiExceptionHandler {

    @ExceptionHandler(RuleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(RuleNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "rule.not-found", ex.getMessage());
    }

    @ExceptionHandler(RuleAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConflict(RuleAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "rule.already-exists", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
