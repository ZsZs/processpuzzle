package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionAlreadyExistsException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionInvalidException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's exceptions onto the {@code ErrorResponse} shape every 4xx in
 * base-widget-api.yaml declares, so the frontend can key on {@code errorId} as a Transloco key
 * rather than parse prose.
 *
 * <p>Only this feature's own exception types are declared. {@code processpuzzle-core}'s
 * {@code ApiExceptionHandler} already claims {@code IllegalArgumentException} and friends;
 * re-declaring one here would make which advice wins depend on bean ordering.
 * {@link ApiAdviceOrder#FEATURE} puts this ahead of core's catch-all — without it these ids would
 * be answered as {@code 500 internal-error}.
 */
@RestControllerAdvice
@Order(ApiAdviceOrder.FEATURE)
public class WidgetApiExceptionHandler {

    @ExceptionHandler(WidgetDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WidgetDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "widget-definition.not-found", ex.getMessage());
    }

    @ExceptionHandler(WidgetDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(WidgetDefinitionAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "widget-definition.already-exists", ex.getMessage());
    }

    @ExceptionHandler(WidgetDefinitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(WidgetDefinitionInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, "widget-definition.invalid", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String message) {
        ErrorResponse body = new ErrorResponse(errorId, message);
        return ResponseEntity.status(status).body(body);
    }
}
