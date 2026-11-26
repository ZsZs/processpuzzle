package com.processpuzzle.ai.assistant;

import org.springframework.ai.chat.client.ChatClient;

/** Gives the assistant the model an organization has subscribed to. */
@FunctionalInterface
public interface AssistantModels {

    /** @throws AssistantNotConfiguredException when the organization has no AI subscription */
    ChatClient clientFor(String organization);

    /** Whether the organization has a model to answer with; the default asks {@link #clientFor}. */
    default boolean isConfigured(String organization) {
        try {
            clientFor(organization);
            return true;
        } catch (AssistantNotConfiguredException e) {
            return false;
        }
    }
}
