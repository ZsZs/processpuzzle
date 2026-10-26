package com.processpuzzle.ai.assistant;

import java.util.Set;

/** The assistant named a definition kind the platform does not author; the message lists the kinds it does. */
public class UnknownDefinitionKindException extends IllegalArgumentException {

    public UnknownDefinitionKindException(String kind, Set<String> known) {
        super("Unknown definition kind '%s'; the kinds that can be authored are %s".formatted(kind, known));
    }
}
