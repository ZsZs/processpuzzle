package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.OrganizationKeyInvalidException;
import com.processpuzzle.app.usecase.exception.OrganizationNotFoundException;
import com.processpuzzle.app.usecase.exception.PageDefinitionNotFoundException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this feature's exceptions onto the {@code ErrorResponse} shape every 4xx in
 * base-app-api.yaml declares, so the frontend can key on {@code errorId} as a Transloco key rather
 * than parse prose.
 *
 * <p>Only this feature's own exception types are declared here. {@code processpuzzle-core}'s
 * {@code ApiExceptionHandler} already claims {@code IllegalArgumentException},
 * {@code IllegalStateException}, {@code JsonProcessingException},
 * {@code MethodArgumentNotValidException} and {@code InvalidDataAccessApiUsageException}; declaring
 * any of those a second time here would make which advice wins depend on bean ordering.
 *
 * <p>{@link ApiAdviceOrder#FEATURE} puts this advice ahead of core's, which is what keeps the ids
 * below from being answered by core's catch-all as {@code 500 internal-error} — see
 * {@link ApiAdviceOrder} for the incident that made the ladder explicit.
 */
@RestControllerAdvice
@Order(ApiAdviceOrder.FEATURE)
public class AppApiExceptionHandler {

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationNotFound(OrganizationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "organization.not-found", ex.getMessage());
    }

    @ExceptionHandler(AppDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAppNotFound(AppDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "app.not-found", ex.getMessage());
    }

    @ExceptionHandler(PageDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePageNotFound(PageDefinitionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "app.page.not-found", ex.getMessage());
    }

    /** 404 rather than 409: the contract declares only 404 for the layout and page endpoints. */
    @ExceptionHandler(AppNotPublishedException.class)
    public ResponseEntity<ErrorResponse> handleNotPublished(AppNotPublishedException ex) {
        return error(HttpStatus.NOT_FOUND, "app.not-published", ex.getMessage());
    }

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleOrganizationExists(OrganizationAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "organization.key.taken", ex.getMessage());
    }

    @ExceptionHandler(AppDefinitionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAppExists(AppDefinitionAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "app.already-exists", ex.getMessage());
    }

    @ExceptionHandler(OrganizationKeyInvalidException.class)
    public ResponseEntity<ErrorResponse> handleKeyInvalid(OrganizationKeyInvalidException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getErrorId(), ex.getMessage());
    }

    /**
     * Reports the first validation problem's identifier so a client that only reads {@code errorId}
     * still gets something actionable; the full list is available from the validate endpoint.
     */
    @ExceptionHandler(AppDefinitionInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(AppDefinitionInvalidException ex) {
        String errorId = ex.getProblems().isEmpty()
                ? "app.validation.failed"
                : ex.getProblems().getFirst().errorId();
        String detail = ex.getProblems().stream()
                .map(problem -> problem.path() + ": " + problem.errorText())
                .reduce((first, second) -> first + " | " + second)
                .orElse(ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, errorId, detail);
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(OrganizationAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "organization.access-denied", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
