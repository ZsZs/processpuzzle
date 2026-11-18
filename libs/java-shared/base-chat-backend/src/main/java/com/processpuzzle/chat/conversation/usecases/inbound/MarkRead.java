package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ReadState;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves the caller's read marker forward, which also makes them follow the conversation. A number past the
 * newest message is cut back to it, and one behind the marker changes nothing — what was read stays read.
 * A caller who may not read the conversation gets 404, as everywhere.
 */
@Service
@Transactional
public class MarkRead {

    private final ConversationRepository conversations;
    private final ResolveAccess access;
    private final ReadMarkers markers;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;

    public MarkRead(ConversationRepository conversations, ResolveAccess access, ReadMarkers markers,
                    OrganizationGuard guard, CurrentPrincipal principal) {
        this.conversations = conversations;
        this.access = access;
        this.markers = markers;
        this.guard = guard;
        this.principal = principal;
    }

    public ReadState markRead(String orgKey, UUID conversationId, long lastReadSeq) {
        guard.requireAccess(orgKey);
        String reader = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Reading needs a signed-in user"));
        if (lastReadSeq < 0) {
            throw new InvalidChatRequestException("'lastReadSeq' must not be negative");
        }
        Conversation conversation = conversations.findByIdAndOrgKey(conversationId, orgKey)
                .orElseThrow(ConversationNotFoundException::conversation);
        if (!access.of(conversation).canRead()) {
            throw ConversationNotFoundException.conversation();
        }
        long newest = conversation.getLastSeq();
        long standing = markers.advanceTo(orgKey, conversationId, reader, Math.min(lastReadSeq, newest));
        return new ReadState(conversationId, standing, newest);
    }
}
