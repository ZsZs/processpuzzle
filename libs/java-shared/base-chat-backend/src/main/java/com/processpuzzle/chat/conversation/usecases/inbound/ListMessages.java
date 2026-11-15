package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One page of a conversation's history, highest {@code seq} first, by cursor: {@code before} is exclusive, so
 * the next page starts at the lowest {@code seq} of the last. A cursor is stable while messages arrive, which
 * an offset is not.
 */
@Service
@Transactional(readOnly = true)
public class ListMessages {

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ResolveAccess access;
    private final OrganizationGuard guard;

    public ListMessages(ConversationRepository conversations, MessageRepository messages, ResolveAccess access,
                        OrganizationGuard guard) {
        this.conversations = conversations;
        this.messages = messages;
        this.access = access;
        this.guard = guard;
    }

    /**
     * @param before return only messages with a lower {@code seq}; null for the newest page
     * @param limit  page size; null for the default, clamped to {@value #MAX_LIMIT}
     */
    public List<Message> list(String orgKey, UUID conversationId, Long before, Integer limit) {
        guard.requireAccess(orgKey);
        Conversation conversation = conversations.findByIdAndOrgKey(conversationId, orgKey)
                .orElseThrow(ConversationNotFoundException::conversation);
        if (!access.of(conversation).canRead()) {
            throw ConversationNotFoundException.conversation();
        }
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        long cursor = before == null ? Long.MAX_VALUE : before;
        return messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId, cursor, PageRequest.of(0, pageSize));
    }
}
