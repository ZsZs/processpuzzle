package com.processpuzzle.ai.assistant;

/** An organization has used up its assistant turns or tokens for the day; the turn is refused before any model call. */
public class AssistantQuotaExceededException extends RuntimeException {

    public AssistantQuotaExceededException(String organization) {
        super("The design assistant's daily limit is used up for organization '%s'".formatted(organization));
    }
}
