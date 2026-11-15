package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes the {@link PlatformEvent}s about conversations — the raw facts base-event matches against the
 * organization's catalog, so a workflow can start when somebody opens a chat. Durable, through the host's
 * event publication registry; individual messages are not published, there are too many.
 */
@Component
public class ConversationPlatformFacts {

    /** The {@code subjectType} of every fact published here. */
    public static final String SUBJECT_TYPE = "chat-conversation";

    /** The payload attributes of the facts about a conversation — what a catalogued event may declare as parameters. */
    public static final Set<String> FACT_ATTRIBUTES =
            Set.of("definitionKey", "subjectType", "subjectDefinition", "subjectId", "title");

    private final ApplicationEventPublisher events;

    public ConversationPlatformFacts(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void opened(Conversation conversation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definitionKey", conversation.getDefinitionKey());
        payload.put("subjectType", conversation.getSubjectType().name());
        payload.put("subjectDefinition", conversation.getSubjectDefinition());
        payload.put("subjectId", conversation.getSubjectId());
        payload.put("title", conversation.getTitle());
        events.publishEvent(PlatformEvent.of(conversation.getOrgKey(), SUBJECT_TYPE, conversation.getId().toString(),
                PlatformEventAction.CREATED, payload, Instant.now()));
    }
}
