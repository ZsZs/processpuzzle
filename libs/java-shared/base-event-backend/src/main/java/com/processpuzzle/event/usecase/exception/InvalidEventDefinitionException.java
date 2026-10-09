package com.processpuzzle.event.usecase.exception;

import java.util.List;

/** An event definition breaks one of {@code EventDefinition.validate()}'s rules; answered 400. */
public class InvalidEventDefinitionException extends RuntimeException {

    public InvalidEventDefinitionException(String id, List<String> errors) {
        super("Event definition '%s' is invalid: %s".formatted(id, String.join("; ", errors)));
    }
}
