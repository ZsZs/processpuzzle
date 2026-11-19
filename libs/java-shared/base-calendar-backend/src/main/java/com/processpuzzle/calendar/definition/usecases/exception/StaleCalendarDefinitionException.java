package com.processpuzzle.calendar.definition.usecases.exception;

public class StaleCalendarDefinitionException extends RuntimeException {

    public StaleCalendarDefinitionException(String definitionKey) {
        super("Calendar definition '%s' was modified concurrently — reload and retry".formatted(definitionKey));
    }
}
