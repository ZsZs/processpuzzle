package com.processpuzzle.ai.assistant;

import java.util.UUID;

/** The session is not one this designer opened in this organization, or it has expired. */
public class AssistantSessionNotFoundException extends RuntimeException {

    public AssistantSessionNotFoundException(UUID id) {
        super("No design assistant session '%s' for this designer in this organization".formatted(id));
    }
}
