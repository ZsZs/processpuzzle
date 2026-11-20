package com.processpuzzle.calendar.entry.usecases.exception;

/** The entry does not exist on that calendar. A caller who may not read the calendar gets the calendar variant instead. */
public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException() {
        super("Entry not found");
    }
}
