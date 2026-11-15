package com.processpuzzle.chat.conversation;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageKind;
import com.processpuzzle.chat.definition.domain.SubjectType;
import java.time.Instant;
import java.util.UUID;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** Builders for the conversation tests: an open conversation of {@code order-discussion} on one order. */
public final class ConversationFixtures {

    public static final String ORG = "org-1";
    public static final String DEFINITION_KEY = "order-discussion";
    public static final String SUBJECT_ID = "9d1c0000-0000-4000-8000-000000000001";

    private ConversationFixtures() {
    }

    public static Conversation conversation() {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .orgKey(ORG)
                .definitionKey(DEFINITION_KEY)
                .subjectType(SubjectType.ENTITY)
                .subjectDefinition("order")
                .subjectId(SUBJECT_ID)
                .status(ConversationStatus.OPEN)
                .bindingKey(Conversation.bindingKeyOf(DEFINITION_KEY, SUBJECT_ID))
                .createdBy("sub-opener")
                .createdByName("Olga Opener")
                .createdAt(Instant.parse("2026-11-15T10:00:00Z"))
                .build();
    }

    /** The provider a use case resolves its optional port through, answering {@code value}. */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfUnique(Mockito.any())).thenReturn(value);
        return provider;
    }

    public static Message message(Conversation conversation, long seq) {
        return Message.builder()
                .conversationId(conversation.getId())
                .seq(seq)
                .orgKey(conversation.getOrgKey())
                .clientMessageId(UUID.randomUUID())
                .authorSub("sub-author")
                .authorDisplayName("Ann Author")
                .kind(MessageKind.TEXT)
                .body("message " + seq)
                .createdAt(Instant.parse("2026-11-15T10:00:00Z").plusSeconds(seq))
                .build();
    }
}
