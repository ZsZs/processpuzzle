package com.processpuzzle.calendar.definition.usecases.exception;

import java.util.List;

public class InvalidCalendarDefinitionException extends RuntimeException {

    private final List<String> violations;

    public InvalidCalendarDefinitionException(String definitionKey, List<String> violations) {
        super("Calendar definition '%s' is invalid: %s".formatted(definitionKey, String.join("; ", violations)));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
