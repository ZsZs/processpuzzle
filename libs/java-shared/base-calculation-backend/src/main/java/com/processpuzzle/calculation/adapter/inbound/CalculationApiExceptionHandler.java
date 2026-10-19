package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.usecase.exception.CalculationAlreadyExistsException;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.calculation.usecase.exception.InvalidParametersException;
import com.processpuzzle.calculation.usecase.exception.RunNotFoundException;
import com.processpuzzle.calculation.usecase.exception.RunNotFreezableException;
import com.processpuzzle.calculation.usecase.exception.StaleCalculationException;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps base-calculation's own exceptions to the {@code errorId}/{@code errorText} shape. Scoped to this module's
 * package and on the {@link ApiAdviceOrder#FEATURE} rung, like the other features' advice.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.calculation")
@Order(ApiAdviceOrder.FEATURE)
public class CalculationApiExceptionHandler {

    @ExceptionHandler({CalculationNotFoundException.class, RunNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, e instanceof RunNotFoundException ? "calculation-run.not-found"
                : "calculation.not-found", e.getMessage());
    }

    @ExceptionHandler(CalculationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(CalculationAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, "calculation.already-exists", e.getMessage());
    }

    @ExceptionHandler({StaleCalculationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleStale(RuntimeException e) {
        return error(HttpStatus.CONFLICT, "calculation.stale-write", e.getMessage());
    }

    @ExceptionHandler(SubjectFrozenException.class)
    public ResponseEntity<ErrorResponse> handleFrozen(SubjectFrozenException e) {
        return error(HttpStatus.CONFLICT, "calculation.subject-frozen", e.getMessage());
    }

    @ExceptionHandler(RunNotFreezableException.class)
    public ResponseEntity<ErrorResponse> handleNotFreezable(RunNotFreezableException e) {
        return error(HttpStatus.CONFLICT, "calculation-run.not-freezable", e.getMessage());
    }

    @ExceptionHandler(InvalidCalculationException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidCalculationException e) {
        return error(HttpStatus.BAD_REQUEST, "calculation.invalid", e.getMessage());
    }

    @ExceptionHandler(InvalidParametersException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameters(InvalidParametersException e) {
        return error(HttpStatus.BAD_REQUEST, "calculation.invalid-parameters", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
