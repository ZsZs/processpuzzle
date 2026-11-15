package com.processpuzzle.chat.definition.usecases.exception;

public class StaleChatDefinitionException extends RuntimeException {

    public StaleChatDefinitionException(String definitionKey) {
        super("Chat definition '%s' was modified concurrently — reload and retry".formatted(definitionKey));
    }
}
