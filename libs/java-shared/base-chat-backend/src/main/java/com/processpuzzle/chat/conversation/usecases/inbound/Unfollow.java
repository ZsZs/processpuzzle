package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops the caller following a conversation: no more live messages, no more unread count. Idempotent, and
 * asks nothing of the conversation — a user must be able to drop one they can no longer read.
 */
@Service
@Transactional
public class Unfollow {

    private final ReadMarkers markers;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;

    public Unfollow(ReadMarkers markers, OrganizationGuard guard, CurrentPrincipal principal) {
        this.markers = markers;
        this.guard = guard;
        this.principal = principal;
    }

    public void unfollow(String orgKey, UUID conversationId) {
        guard.requireAccess(orgKey);
        String user = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Unfollowing needs a signed-in user"));
        markers.unfollow(orgKey, conversationId, user);
    }
}
