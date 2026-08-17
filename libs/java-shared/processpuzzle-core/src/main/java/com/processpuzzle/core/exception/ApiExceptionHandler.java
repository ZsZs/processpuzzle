package com.processpuzzle.core.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Generic exceptions, for every module. Feature-specific advice lives beside its own endpoint
 * ({@code DocumentApiExceptionHandler}, {@code RuleApiExceptionHandler}, {@code AppApiExceptionHandler})
 * and runs first, at {@link ApiAdviceOrder#FEATURE}; because this advice already claims the exceptions
 * below, declaring any of them a second time in a feature advice would make which one wins depend on
 * bean ordering.
 *
 * <p>The catch-all for everything not listed here is {@link UnhandledExceptionHandler}, in a class of
 * its own and last — see there for what happened when it sat in this one.
 *
 * <p>Every body is an {@link ApiError} — see that record for why it is not the generated
 * {@code ErrorResponse}.
 */
@RestControllerAdvice
@Order(ApiAdviceOrder.GENERIC)
public class ApiExceptionHandler {

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

    /**
     * An unparseable request body, declared on Spring's own exception rather than on the parser's.
     * It cannot rely on the {@code JsonProcessingException} handler below via Spring's fallback to the
     * cause: Spring Boot 4 reads bodies with <strong>Jackson 3</strong>, whose exceptions live in
     * {@code tools.jackson.core} and are unrelated to Jackson 2's {@code com.fasterxml.jackson.core}
     * hierarchy. Nothing matched, so the catch-all answered {@code 500} for what is plainly a caller's
     * mistake — visible only over HTTP, which is where it was found.
     *
     * <p>The parser's own message is passed through: it describes the caller's payload, not ours.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "request.malformed-payload", "Could not parse the request payload: " + ex.getMostSpecificCause().getMessage());
    }

    /**
     * Jackson 2 parse failures raised by our own code — reading a YAML sample document, say — rather than
     * by the HTTP layer, which {@link #handleUnreadableBody} serves. Same id for both: to a caller,
     * "your payload does not parse" is one refusal, and it is the id the Cloud Function emits for it too.
     *
     * <p>Spring's fallback to an exception's <em>cause</em> is why the catch-all cannot live in this
     * class: a handler for {@code Exception} here would match first and answer 500. {@code handleBadRequest}
     * depends on that fallback for an invalid {@code UUID} path variable.
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ApiError> handleParseError(JsonProcessingException ex) {
        return error(HttpStatus.BAD_REQUEST, "request.malformed-payload", "Could not parse the request payload: " + ex.getOriginalMessage());
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
     * A required request parameter (e.g. query parameter) was omitted by the caller.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "request.missing-parameter", ex.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ApiError(errorId, errorText));
    }
}
