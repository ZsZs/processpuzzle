package com.processpuzzle.calendar.entry.usecases.exception;

import java.util.List;

/** The entry is well formed but breaks a rule of its calendar. */
public class InvalidBookingException extends RuntimeException {

    private final List<String> violations;

    public InvalidBookingException(List<String> violations) {
        super("The entry breaks the rules of its calendar: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
