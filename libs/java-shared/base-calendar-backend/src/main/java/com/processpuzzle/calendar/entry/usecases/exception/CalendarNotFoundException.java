package com.processpuzzle.calendar.entry.usecases.exception;

/**
 * The calendar does not exist, is not released, or the caller may not read it. One exception for all three, so the
 * answer does not reveal that a calendar a caller may not see is there.
 */
public class CalendarNotFoundException extends RuntimeException {

    public CalendarNotFoundException() {
        super("Calendar not found");
    }
}
