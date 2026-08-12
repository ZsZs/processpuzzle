package com.processpuzzle.core.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Generic exceptions, for every module. Feature-specific advice lives beside its own endpoint
 * ({@code DocumentApiExceptionHandler}, {@code RuleApiExceptionHandler}, {@code AppApiExceptionHandler});
 * because this advice is unordered and already claims the exceptions below, declaring any of them a
 * second time in a feature advice would make which one wins depend on bean ordering.
 *
 * <p>Every body is an {@link ApiError} — see that record for why it is not the generated
 * {@code ErrorResponse}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "request.invalid-argument", ex.getMessage());
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiError> handleDaoApiUsage(InvalidDataAccessApiUsageException ex) {
        // RSQL argument-coercion errors surface here because they're raised inside the
        // Specification lambda and Spring's HibernateExceptionTranslator wraps them. Reported with the
        // same errorId as a direct IllegalArgumentException, because to the caller it is the same
        // mistake — a bad argument — and the wrapping is an implementation detail of our persistence.
        Throwable root = ex.getMostSpecificCause();
        if (root instanceof IllegalArgumentException) {
            return error(HttpStatus.BAD_REQUEST, "request.invalid-argument", root.getMessage());
        }
        throw ex;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflictState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "request.illegal-state", ex.getMessage());
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ApiError> handleParseError(JsonProcessingException ex) {
        return error(HttpStatus.BAD_REQUEST, "request.malformed-payload", "Could not parse YAML: " + ex.getOriginalMessage());
    }

    /**
     * Bean-validation failures, flattened into {@code errorText} as {@code field: message} pairs.
     *
     * <p>This used to return a bare {@code {field: message}} map, whose key set depended on the
     * payload — the one shape that cannot be reconciled with a declared schema, and one no client ever
     * read. If per-field binding is wanted, it belongs in a declared schema of its own rather than in an
     * undeclared map that happens to occupy the error slot.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "request.validation-failed", fieldErrors);
    }

    /**
     * Anything not handled above, so that a 500 carries the same shape as every other status rather
     * than Spring Boot's default {@code {timestamp, status, error, path}} — whose {@code error} holds a
     * reason phrase, not a message, and would therefore mean something different from ours.
     *
     * <p>{@code errorText} is deliberately generic rather than {@code ex.getMessage()}: an unexpected
     * exception's message is the likeliest place for an internal detail — a query, a path, a host — to
     * leak to a caller. The exception itself is logged in full, so nothing is lost to whoever operates
     * the service.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception while serving a request", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected server error.");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ApiError(errorId, errorText));
    }
}
