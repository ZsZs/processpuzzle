package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.api.ConversationsApi;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.FindConversations;
import com.processpuzzle.chat.conversation.usecases.inbound.GetConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ListMessages;
import com.processpuzzle.chat.conversation.usecases.inbound.ListUnread;
import com.processpuzzle.chat.conversation.usecases.inbound.MarkRead;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.PostMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.Unfollow;
import com.processpuzzle.chat.model.ChatMessage;
import com.processpuzzle.chat.model.ChatSubjectType;
import com.processpuzzle.chat.model.Conversation;
import com.processpuzzle.chat.model.MarkReadRequest;
import com.processpuzzle.chat.model.OpenConversationRequest;
import com.processpuzzle.chat.model.PostMessageRequest;
import com.processpuzzle.chat.model.ReadState;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code ConversationsApi}, which carries the messages of a conversation too. */
@RestController
public class ConversationEndpoint implements ConversationsApi {

    private final FindConversations findConversations;
    private final OpenConversation openConversation;
    private final GetConversation getConversation;
    private final ListMessages listMessages;
    private final PostMessage postMessage;
    private final MarkRead markRead;
    private final Unfollow unfollow;
    private final ListUnread listUnread;
    private final ConversationMapper mapper;

    public ConversationEndpoint(FindConversations findConversations, OpenConversation openConversation,
                                GetConversation getConversation, ListMessages listMessages, PostMessage postMessage,
                                MarkRead markRead, Unfollow unfollow, ListUnread listUnread, ConversationMapper mapper) {
        this.findConversations = findConversations;
        this.openConversation = openConversation;
        this.getConversation = getConversation;
        this.listMessages = listMessages;
        this.postMessage = postMessage;
        this.markRead = markRead;
        this.unfollow = unfollow;
        this.listUnread = listUnread;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<Conversation>> findConversations(String orgKey, ChatSubjectType subjectType, String subjectId,
                                                                String subjectDefinition, String definition) {
        return ResponseEntity.ok(findConversations
                .find(orgKey, mapper.toDomain(subjectType), subjectId, subjectDefinition, definition).stream()
                .map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<Conversation> openConversation(String orgKey, OpenConversationRequest request) {
        var opened = openConversation.open(orgKey, request.getDefinitionKey(), request.getSubjectId(), request.getTitle());
        return new ResponseEntity<>(mapper.toModel(opened), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Conversation> getConversation(String orgKey, UUID conversationId) {
        return ResponseEntity.ok(mapper.toModel(getConversation.get(orgKey, conversationId)));
    }

    @Override
    public ResponseEntity<List<ChatMessage>> listMessages(String orgKey, UUID conversationId, Long before, Integer limit) {
        return ResponseEntity.ok(listMessages.list(orgKey, conversationId, before, limit).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ReadState> markConversationRead(String orgKey, UUID conversationId, MarkReadRequest request) {
        return ResponseEntity.ok(mapper.toModel(markRead.markRead(orgKey, conversationId, request.getLastReadSeq())));
    }

    @Override
    public ResponseEntity<Void> unfollowConversation(String orgKey, UUID conversationId) {
        unfollow.unfollow(orgKey, conversationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Conversation>> listFollowedConversations(String orgKey) {
        return ResponseEntity.ok(listUnread.list(orgKey).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<ChatMessage> postMessage(String orgKey, UUID conversationId, PostMessageRequest request) {
        PostedMessage posted = postMessage.post(orgKey, conversationId, request.getClientMessageId(), request.getBody());
        return new ResponseEntity<>(mapper.toModel(posted.message()), posted.created() ? HttpStatus.CREATED : HttpStatus.OK);
    }
}
