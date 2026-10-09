package com.processpuzzle.event.usecase.exception;

public class EventDefinitionNotFoundException extends RuntimeException {

    public EventDefinitionNotFoundException(String orgKey, String id) {
        super("No event definition '%s' in organization '%s'".formatted(id, orgKey));
    }
}
