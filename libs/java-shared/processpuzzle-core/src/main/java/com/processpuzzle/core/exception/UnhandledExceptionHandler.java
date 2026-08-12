package com.processpuzzle.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The last advice: anything no other advice claimed, so a 500 carries the same shape as every other
 * status rather than Spring Boot's default {@code {timestamp, status, error, path}} — whose {@code error}
 * holds a reason phrase, not a message, and would therefore mean something different from ours.
 *
 * <p>It sits in a class of its own rather than beside the handlers in {@link ApiExceptionHandler}, for
 * two reasons that only showed up when both backends were asked the same question over HTTP:
 * <ul>
 *   <li>{@link ApiAdviceOrder#CATCH_ALL} makes it lose every tie. Sharing a class with the specific
 *       handlers would give the catch-all that class's precedence, and it would answer for the feature
 *       advices too.</li>
 *   <li>Within one advice class a direct match on {@code Exception} beats Spring's fallback to the
 *       exception's <em>cause</em>. An invalid {@code UUID} path variable arrives as
 *       {@code MethodArgumentTypeMismatchException} <em>caused by</em> an {@code IllegalArgumentException}:
 *       with the catch-all in the same class it was answered {@code 500} instead of the {@code 400}
 *       {@code ApiExceptionHandler.handleBadRequest} gives it.</li>
 * </ul>
 *
 * <p>{@code errorText} is deliberately generic rather than {@code ex.getMessage()}: an unexpected
 * exception's message is the likeliest place for an internal detail — a query, a path, a host — to leak
 * to a caller. The exception itself is logged in full, so nothing is lost to whoever operates the
 * service.
 */
@RestControllerAdvice
@Order(ApiAdviceOrder.CATCH_ALL)
public class UnhandledExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UnhandledExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception while serving a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("internal-error", "Unexpected server error."));
    }
}
