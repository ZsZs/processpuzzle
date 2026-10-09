package com.processpuzzle.event.usecase.exception;

public class EventDefinitionAlreadyExistsException extends RuntimeException {

    public EventDefinitionAlreadyExistsException(String orgKey, String id) {
        super("Event definition '%s' already exists in organization '%s'".formatted(id, orgKey));
    }
}
