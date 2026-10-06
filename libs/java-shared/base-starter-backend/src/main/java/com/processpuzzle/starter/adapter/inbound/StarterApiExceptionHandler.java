package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import com.processpuzzle.starter.model.ImportReport;
import com.processpuzzle.starter.registry.RegistryUnavailableException;
import com.processpuzzle.starter.usecase.ImportRejectedException;
import com.processpuzzle.starter.usecase.StarterNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a refused import with its {@code ImportReport}, as base-starter-api.yaml declares: 413 for a
 * bundle over a size limit, 409 for an organization holding instance data, 422 for everything else.
 * An unknown starter is 404 and an unreadable registry 503. Scoped to this feature's package and ordered ahead
 * of core's catch-all — see {@code ApiAdviceOrder} for why both matter.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.starter")
@Order(ApiAdviceOrder.FEATURE)
public class StarterApiExceptionHandler {

    private final StarterMapper mapper;

    public StarterApiExceptionHandler(StarterMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ImportRejectedException.class)
    public ResponseEntity<ImportReport> handleRejected(ImportRejectedException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case HOLDS_INSTANCE_DATA -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(mapper.toModel(ex.getReport()));
    }

    @ExceptionHandler(StarterNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(StarterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("starter.not-found", ex.getMessage()));
    }

    @ExceptionHandler(RegistryUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRegistryUnavailable(RegistryUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("starter.registry-unavailable", ex.getMessage()));
    }
}
