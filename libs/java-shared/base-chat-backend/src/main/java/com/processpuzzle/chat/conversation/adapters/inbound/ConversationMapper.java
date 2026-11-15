package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.ReadState;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.model.ChatMessage;
import com.processpuzzle.chat.model.ChatSubjectType;
import com.processpuzzle.chat.model.ConversationPermissions;
import com.processpuzzle.chat.model.ConversationStatus;
import com.processpuzzle.chat.model.MessageKind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * Between the generated {@code com.processpuzzle.chat.model} DTOs and the domain. The enums are separate
 * vocabularies on each side and are translated by name.
 */
@Component
public class ConversationMapper {

    public com.processpuzzle.chat.model.Conversation toModel(ConversationView view) {
        Conversation conversation = view.conversation();
        return new com.processpuzzle.chat.model.Conversation()
                .id(conversation.getId())
                .definitionKey(conversation.getDefinitionKey())
                .subjectType(ChatSubjectType.valueOf(conversation.getSubjectType().name()))
                .subjectDefinition(conversation.getSubjectDefinition())
                .subjectId(conversation.getSubjectId())
                .title(conversation.getTitle())
                .status(ConversationStatus.valueOf(conversation.getStatus().name()))
                .lastSeq(conversation.getLastSeq())
                .createdBy(conversation.getCreatedByName())
                .createdAt(offset(conversation.getCreatedAt()))
                .permissions(new ConversationPermissions().canWrite(view.access().canWrite()))
                .following(view.following())
                .lastReadSeq(view.lastReadSeq())
                .unread(view.unread());
    }

    public com.processpuzzle.chat.model.ReadState toModel(ReadState state) {
        return new com.processpuzzle.chat.model.ReadState()
                .conversationId(state.conversationId())
                .lastReadSeq(state.lastReadSeq())
                .lastSeq(state.lastSeq())
                .unread(state.unread());
    }

    public ChatMessage toModel(Message message) {
        return new ChatMessage()
                .seq(message.getSeq())
                .clientMessageId(message.getClientMessageId())
                .authorId(message.getAuthorSub())
                .authorName(message.getAuthorDisplayName())
                .kind(MessageKind.valueOf(message.getKind().name()))
                .body(message.getBody())
                .createdAt(offset(message.getCreatedAt()));
    }

    public SubjectType toDomain(ChatSubjectType subjectType) {
        return SubjectType.valueOf(subjectType.name());
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
