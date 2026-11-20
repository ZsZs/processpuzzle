package com.processpuzzle.calendar.entry.usecases.exception;

/** The caller edited an entry from a version that is no longer the stored one. */
public class StaleEntryException extends RuntimeException {

    public StaleEntryException() {
        super("The entry was modified concurrently - reload and retry");
    }
}
