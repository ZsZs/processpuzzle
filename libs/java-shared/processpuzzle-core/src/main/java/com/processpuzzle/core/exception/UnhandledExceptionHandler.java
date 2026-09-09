package com.processpuzzle.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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

    /**
     * Unclaimed does not mean unexpected. Spring's own MVC exceptions all implement {@link
     * ErrorResponse} and already carry the status they mean — {@code NoResourceFoundException} a 404,
     * {@code HttpRequestMethodNotSupportedException} a 405, {@code HttpMediaTypeNotSupportedException} a
     * 415 — so the first question to ask about an exception nothing else claimed is whether it has
     * already answered it.
     *
     * <p>Asked here rather than as its own {@code @ExceptionHandler} because {@link ErrorResponse} is an
     * interface on the exception, not a {@code Throwable} subtype, and {@code @ExceptionHandler} can
     * only name the latter. Naming the concrete classes instead would mean a handler each and a new one
     * every time Spring adds one; one {@code instanceof} covers the family.
     *
     * <p>This is not hypothetical tidying. Until it was added, a browser asking for {@code /favicon.svg}
     * got a {@code 500} and put a full stack trace in the log at {@code ERROR}, several times a page
     * load — noise loud enough to bury a real 500, reporting a routine miss as a server fault.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return declaredStatus(ex, errorResponse);
        }
        LOG.error("Unhandled exception while serving a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("internal-error", "Unexpected server error."));
    }

    /**
     * Answers with the status the exception itself declares.
     *
     * <p>Logged at {@code DEBUG} for a 4xx and {@code ERROR} for anything else: a client asking for
     * something that is not there is a normal outcome of serving requests, whereas a 5xx that happens to
     * carry its own status — an async timeout, say — is still a fault worth a stack trace.
     *
     * <p>{@code errorText} is the exception's own message here, unlike the generic text above. Spring
     * puts it in {@link ProblemDetail#getDetail()} precisely because it is meant for the client, and it
     * describes the request the caller made rather than anything about how this service is built.
     */
    private ResponseEntity<ApiError> declaredStatus(Exception ex, ErrorResponse errorResponse) {
        HttpStatusCode statusCode = errorResponse.getStatusCode();
        if (statusCode.is4xxClientError()) {
            LOG.debug("Request rejected with {}: {}", statusCode, ex.getMessage());
        } else {
            LOG.error("Request failed with self-declared status {}", statusCode, ex);
        }
        return ResponseEntity.status(statusCode).body(new ApiError(errorId(statusCode), detailOf(ex, errorResponse)));
    }

    /**
     * A stable id derived from the status rather than a hand-kept table: {@code 404} becomes
     * {@code request.not-found}, {@code 405} {@code request.method-not-allowed}. The status already
     * carries the meaning, so deriving the id keeps the two from disagreeing and needs no upkeep when
     * Spring throws a status this service has not seen before.
     */
    private static String errorId(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null
                ? "request.status-" + statusCode.value()
                : "request." + status.name().toLowerCase().replace('_', '-');
    }

    private static String detailOf(Exception ex, ErrorResponse errorResponse) {
        String detail = errorResponse.getBody().getDetail();
        return detail != null ? detail : ex.getMessage();
    }
}
