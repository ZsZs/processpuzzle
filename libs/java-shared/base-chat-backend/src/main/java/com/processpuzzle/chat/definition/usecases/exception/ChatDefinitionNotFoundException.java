package com.processpuzzle.chat.definition.usecases.exception;

public class ChatDefinitionNotFoundException extends RuntimeException {

    public ChatDefinitionNotFoundException(String orgKey, String definitionKey) {
        super("Chat definition '%s' not found in organization '%s'".formatted(definitionKey, orgKey));
    }
}
