package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageKind;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotWritableException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.events.MessagePostedEvent;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends a message to a conversation.
 *
 * <p>Access is decided first, from a plain read: 404 for a conversation the caller may not read, 403 when
 * they may read but hold no write role, 409 when the conversation is not open or its STATE_BOUND subject is
 * not in a writable state. Only then is the row locked, so the lock covers the numbering and not the
 * decision. The lock is only granted while the conversation is open — it can be closed in between — and
 * under it a replay of an earlier post is answered with the stored message, so a replay takes no number.
 *
 * <p>The author follows the conversation from then on, and the stored message is announced with a
 * {@link MessagePostedEvent} that listeners see only once the transaction has committed.
 */
@Service
@Transactional
public class PostMessage {

    /** What the stored column holds. */
    static final int HARD_LIMIT = 10_000;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;
    private final ReadMarkers markers;
    private final ApplicationEventPublisher events;
    private final int maxLength;

    public PostMessage(ConversationRepository conversations, MessageRepository messages, ResolveAccess access,
                       OrganizationGuard guard, CurrentPrincipal principal, ReadMarkers markers,
                       ApplicationEventPublisher events, @Value("${base-chat.max-message-length:4000}") int maxLength) {
        this.conversations = conversations;
        this.messages = messages;
        this.access = access;
        this.guard = guard;
        this.principal = principal;
        this.markers = markers;
        this.events = events;
        this.maxLength = Math.min(maxLength, HARD_LIMIT);
    }

    public PostedMessage post(String orgKey, UUID conversationId, UUID clientMessageId, String body) {
        guard.requireAccess(orgKey);
        String author = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Posting needs a signed-in user"));
        validate(clientMessageId, body);

        Conversation conversation = conversations.findByIdAndOrgKey(conversationId, orgKey)
                .orElseThrow(ConversationNotFoundException::conversation);
        ConversationAccess allowed = access.of(conversation);
        if (!allowed.canRead()) {
            throw ConversationNotFoundException.conversation();
        }
        if (!allowed.mayWrite()) {
            throw new ChatAccessDeniedException("Your roles do not allow posting in this conversation");
        }
        if (!allowed.writable()) {
            throw notWritable();
        }

        if (conversations.lockIfStatus(conversationId, orgKey, ConversationStatus.OPEN) == 0) {
            throw notWritable();
        }
        Optional<Message> replay = messages.findByConversationIdAndAuthorSubAndClientMessageId(conversationId, author, clientMessageId);
        if (replay.isPresent()) {
            return new PostedMessage(replay.get(), false);
        }
        conversations.advanceSeq(conversationId, orgKey);
        Message message = Message.builder()
                .conversationId(conversationId)
                .seq(conversations.lastSeqOf(conversationId))
                .orgKey(orgKey)
                .clientMessageId(clientMessageId)
                .authorSub(author)
                .authorDisplayName(principal.displayName().orElse(null))
                .kind(MessageKind.TEXT)
                .body(body)
                .createdAt(Instant.now())
                .build();
        Message stored = messages.save(message);
        markers.followAfterPosting(orgKey, conversationId, author, stored.getSeq());
        events.publishEvent(new MessagePostedEvent(orgKey, conversationId, stored));
        return new PostedMessage(stored, true);
    }

    private void validate(UUID clientMessageId, String body) {
        if (clientMessageId == null) {
            throw new InvalidChatRequestException("'clientMessageId' is required");
        }
        if (body == null || body.isBlank()) {
            throw new InvalidChatRequestException("A message needs a body");
        }
        if (body.length() > maxLength) {
            throw new InvalidChatRequestException("A message is at most %d characters".formatted(maxLength));
        }
    }

    private static ConversationNotWritableException notWritable() {
        return new ConversationNotWritableException("The conversation does not accept messages right now");
    }
}
