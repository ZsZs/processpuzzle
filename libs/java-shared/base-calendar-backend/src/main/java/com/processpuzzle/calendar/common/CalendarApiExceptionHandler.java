package com.processpuzzle.calendar.common;

import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidRevisionException;
import com.processpuzzle.calendar.definition.usecases.exception.StaleCalendarDefinitionException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.calendar.entry.usecases.exception.StaleEntryException;
import com.processpuzzle.core.exception.ApiAdviceOrder;
import com.processpuzzle.shared.model.ErrorResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps base-calendar's own exceptions to the {@code errorId}/{@code errorText} shape. Scoped to this module's
 * package and on the {@link ApiAdviceOrder#FEATURE} rung, so it neither shadows nor is shadowed by another
 * feature's advice claiming the same Spring exception.
 *
 * <p>404 means "not yours to know" as well as "not there"; 403 only "you may look but not do this". The
 * messages never name a calendar or an entry the caller may not read.
 */
@RestControllerAdvice(basePackages = "com.processpuzzle.calendar")
@Order(ApiAdviceOrder.FEATURE)
public class CalendarApiExceptionHandler {

    @ExceptionHandler(CalendarDefinitionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDefinitionNotFound(CalendarDefinitionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "calendar.definition.not-found", e.getMessage());
    }

    @ExceptionHandler({StaleCalendarDefinitionException.class, StaleEntryException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleStale(RuntimeException e) {
        return error(HttpStatus.CONFLICT, "calendar.stale-write", e.getMessage());
    }

    @ExceptionHandler(InvalidCalendarDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDefinition(InvalidCalendarDefinitionException e) {
        return error(HttpStatus.BAD_REQUEST, "calendar.definition.invalid", e.getMessage());
    }

    @ExceptionHandler(InvalidRevisionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRevision(InvalidRevisionException e) {
        return error(HttpStatus.BAD_REQUEST, "calendar.definition.invalid-revision", e.getMessage());
    }

    @ExceptionHandler(CalendarNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCalendarNotFound(CalendarNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "calendar.not-found", e.getMessage());
    }

    @ExceptionHandler(EntryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntryNotFound(EntryNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "calendar.entry.not-found", e.getMessage());
    }

    @ExceptionHandler(CalendarAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(CalendarAccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "calendar.access-denied", e.getMessage());
    }

    @ExceptionHandler(InvalidBookingException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBooking(InvalidBookingException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "calendar.booking.invalid", e.getMessage());
    }

    @ExceptionHandler(InvalidEntryRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidEntryRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "calendar.invalid-request", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String errorId, String errorText) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorId, errorText));
    }
}
