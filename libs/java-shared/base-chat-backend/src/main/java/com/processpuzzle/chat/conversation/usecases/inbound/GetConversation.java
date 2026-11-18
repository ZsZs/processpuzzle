package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One conversation with what the caller may do in it and how far they have read it; 404 for one they may not
 * read, as for an unknown one.
 */
@Service
@Transactional(readOnly = true)
public class GetConversation {

    private final ConversationRepository conversations;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final ReadMarkers markers;
    private final CurrentPrincipal principal;

    public GetConversation(ConversationRepository conversations, ResolveAccess access, OrganizationGuard guard,
                           ReadMarkers markers, CurrentPrincipal principal) {
        this.conversations = conversations;
        this.access = access;
        this.guard = guard;
        this.markers = markers;
        this.principal = principal;
    }

    public ConversationView get(String orgKey, UUID conversationId) {
        guard.requireAccess(orgKey);
        Conversation conversation = conversations.findByIdAndOrgKey(conversationId, orgKey)
                .orElseThrow(ConversationNotFoundException::conversation);
        ConversationAccess resolved = access.of(conversation);
        if (!resolved.canRead()) {
            throw ConversationNotFoundException.conversation();
        }
        Long lastRead = principal.subject()
                .map(user -> markers.lastRead(user, List.of(conversationId)).get(conversationId)).orElse(null);
        return new ConversationView(conversation, resolved, lastRead);
    }
}
