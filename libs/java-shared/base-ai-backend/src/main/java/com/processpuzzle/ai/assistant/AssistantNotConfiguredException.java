package com.processpuzzle.ai.assistant;

/** The organization has not subscribed to a model service, so there is no assistant for it. */
public class AssistantNotConfiguredException extends RuntimeException {

    public AssistantNotConfiguredException(String organization) {
        super("Organization '%s' has no AI subscription".formatted(organization));
    }
}
