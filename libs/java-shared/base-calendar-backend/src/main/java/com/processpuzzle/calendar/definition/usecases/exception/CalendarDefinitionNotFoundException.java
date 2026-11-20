package com.processpuzzle.calendar.definition.usecases.exception;

public class CalendarDefinitionNotFoundException extends RuntimeException {

    public CalendarDefinitionNotFoundException(String orgKey, String definitionKey) {
        super("Calendar definition '%s' not found in organization '%s'".formatted(definitionKey, orgKey));
    }
}
