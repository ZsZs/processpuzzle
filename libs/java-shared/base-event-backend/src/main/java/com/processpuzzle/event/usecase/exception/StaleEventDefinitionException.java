package com.processpuzzle.event.usecase.exception;

public class StaleEventDefinitionException extends RuntimeException {

    public StaleEventDefinitionException(String id) {
        super("Event definition '%s' was modified concurrently — reload and retry".formatted(id));
    }
}
