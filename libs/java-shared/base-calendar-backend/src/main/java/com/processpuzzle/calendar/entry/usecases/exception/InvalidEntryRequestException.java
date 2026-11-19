package com.processpuzzle.calendar.entry.usecases.exception;

/** The request is malformed: a window that ends before it starts, or is longer than allowed. */
public class InvalidEntryRequestException extends RuntimeException {

    public InvalidEntryRequestException(String message) {
        super(message);
    }
}
