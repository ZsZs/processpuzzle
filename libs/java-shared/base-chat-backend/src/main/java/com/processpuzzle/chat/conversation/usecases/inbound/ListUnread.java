package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversations the caller follows, with their unread counts, for badges and an inbox. Only what the
 * caller may still read: a conversation whose definition, subject or role they have lost is left out, not
 * reported. At most {@value #MAX_FOLLOWED}, the most recently touched first.
 */
@Service
@Transactional(readOnly = true)
public class ListUnread {

    static final int MAX_FOLLOWED = 200;

    private final ReadMarkerRepository markers;
    private final ConversationRepository conversations;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;

    public ListUnread(ReadMarkerRepository markers, ConversationRepository conversations, ResolveAccess access,
                      OrganizationGuard guard, CurrentPrincipal principal) {
        this.markers = markers;
        this.conversations = conversations;
        this.access = access;
        this.guard = guard;
        this.principal = principal;
    }

    public List<ConversationView> list(String orgKey) {
        guard.requireAccess(orgKey);
        String user = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Listing what you follow needs a signed-in user"));
        List<ReadMarker> followed = markers.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(orgKey, user, PageRequest.of(0, MAX_FOLLOWED));
        Map<UUID, Conversation> byId = conversations.findAllById(followed.stream().map(ReadMarker::getConversationId).toList()).stream()
                .filter(conversation -> orgKey.equals(conversation.getOrgKey()))
                .collect(Collectors.toMap(Conversation::getId, Function.identity()));
        List<ConversationView> views = new ArrayList<>();
        for (ReadMarker marker : followed) {
            Conversation conversation = byId.get(marker.getConversationId());
            if (conversation != null) {
                readable(conversation, marker).ifPresent(views::add);
            }
        }
        return views;
    }

    private java.util.Optional<ConversationView> readable(Conversation conversation, ReadMarker marker) {
        try {
            var resolved = access.of(conversation);
            return resolved.canRead()
                    ? java.util.Optional.of(new ConversationView(conversation, resolved, marker.getLastReadSeq()))
                    : java.util.Optional.empty();
        } catch (ConversationNotFoundException e) {
            return java.util.Optional.empty();
        }
    }
}
