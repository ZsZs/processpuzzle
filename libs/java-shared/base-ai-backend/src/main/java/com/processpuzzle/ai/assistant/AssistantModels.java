package com.processpuzzle.ai.assistant;

import org.springframework.ai.chat.client.ChatClient;

/** Gives the assistant the model an organization has subscribed to. */
@FunctionalInterface
public interface AssistantModels {

    /** @throws AssistantNotConfiguredException when the organization has no AI subscription */
    ChatClient clientFor(String organization);
}
