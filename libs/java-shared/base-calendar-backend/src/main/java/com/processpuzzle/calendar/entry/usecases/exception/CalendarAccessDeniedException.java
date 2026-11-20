package com.processpuzzle.calendar.entry.usecases.exception;

/** The caller may read the calendar but not do what they asked: book, edit an entry that is not theirs, cancel it. */
public class CalendarAccessDeniedException extends RuntimeException {

    public CalendarAccessDeniedException(String message) {
        super(message);
    }
}
