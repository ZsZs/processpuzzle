package com.processpuzzle.calendar.definition.usecases.exception;

/** The {@code revision} query parameter names neither the draft nor the released row of a definition. */
public class InvalidRevisionException extends RuntimeException {

    public InvalidRevisionException(String message) {
        super(message);
    }
}
