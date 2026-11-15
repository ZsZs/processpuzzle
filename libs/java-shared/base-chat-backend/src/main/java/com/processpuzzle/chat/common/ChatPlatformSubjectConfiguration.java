package com.processpuzzle.chat.common;

import com.processpuzzle.chat.conversation.usecases.inbound.ConversationPlatformFacts;
import com.processpuzzle.core.definition.PlatformSubject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@code chat-conversation} as a subject the platform publishes facts about, so the event catalog
 * can name it: an event definition's subject is otherwise required to be an entity type.
 */
@Configuration(proxyBeanMethods = false)
public class ChatPlatformSubjectConfiguration {

    @Bean
    public PlatformSubject chatConversationPlatformSubject() {
        return new PlatformSubject(ConversationPlatformFacts.SUBJECT_TYPE, ConversationPlatformFacts.FACT_ATTRIBUTES);
    }
}
