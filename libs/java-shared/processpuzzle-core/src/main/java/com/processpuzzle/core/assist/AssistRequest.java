package com.processpuzzle.core.assist;

import java.util.Map;

/**
 * What a user asks an assist task for: the words to turn into an artifact, the other form values the task needs, and
 * what the field holds now.
 *
 * @param text       the user's own description of what the artifact should do
 * @param parameters the target's other form values the task asks for, by name; each task documents its keys
 * @param current    the field's present value, or empty when it has none, so a task can repair or extend it
 */
public record AssistRequest(String text, Map<String, String> parameters, String current) {

    public AssistRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        current = current == null ? "" : current;
    }
}
