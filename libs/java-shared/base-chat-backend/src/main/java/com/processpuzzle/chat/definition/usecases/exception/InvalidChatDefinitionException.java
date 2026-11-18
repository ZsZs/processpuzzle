package com.processpuzzle.chat.definition.usecases.exception;

import java.util.List;

public class InvalidChatDefinitionException extends RuntimeException {

    private final List<String> violations;

    public InvalidChatDefinitionException(String definitionKey, List<String> violations) {
        super("Chat definition '%s' is invalid: %s".formatted(definitionKey, String.join("; ", violations)));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
