package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides what a new live connection may carry. One connection per user and tab, for <em>all</em> the
 * conversations the user follows — a page with unread badges would otherwise use up the browser's handful of
 * connections per origin.
 *
 * <p>Authorization happens here, on the caller's own thread, where their token is: a message is posted by
 * somebody else, on another thread, and could not be checked against the recipient's roles. So the connection
 * carries a snapshot — the followed conversations the caller may read now — and ends at the token's expiry,
 * after which the client reconnects and the snapshot is taken again. A role lost mid-connection takes effect
 * at the latest then; history, posting and every other call check afresh each time.
 *
 * <p>A client that reconnects says how far it had got in each conversation ({@code after}); what it missed
 * comes back as {@code replay}, or as {@code resync} when it missed more than {@value #REPLAY_LIMIT}.
 */
@Service
@Transactional(readOnly = true)
public class OpenChatStream {

    static final int REPLAY_LIMIT = 200;

    private final ReadMarkerRepository markers;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;
    private final Duration maxAge;

    public OpenChatStream(ReadMarkerRepository markers, ConversationRepository conversations, MessageRepository messages,
                          ResolveAccess access, OrganizationGuard guard, CurrentPrincipal principal,
                          @Value("${base-chat.stream.max-age-minutes:15}") long maxAgeMinutes) {
        this.markers = markers;
        this.conversations = conversations;
        this.messages = messages;
        this.access = access;
        this.guard = guard;
        this.principal = principal;
        this.maxAge = Duration.ofMinutes(Math.max(1, maxAgeMinutes));
    }

    /**
     * @param after for each conversation the client already has, the newest {@code seq} it holds; may be empty
     */
    public StreamGrant open(String orgKey, Map<UUID, Long> after) {
        guard.requireAccess(orgKey);
        String user = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("A live stream needs a signed-in user"));
        Instant now = Instant.now();

        List<ReadMarker> followed = markers.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(
                orgKey, user, PageRequest.of(0, ListUnread.MAX_FOLLOWED));
        Map<UUID, Conversation> readable = readable(orgKey, followed);

        List<Message> replay = new ArrayList<>();
        Set<UUID> resync = new LinkedHashSet<>();
        after.forEach((conversationId, clientSeq) -> {
            Conversation conversation = readable.get(conversationId);
            if (conversation != null && conversation.getLastSeq() > clientSeq) {
                if (conversation.getLastSeq() - clientSeq > REPLAY_LIMIT) {
                    resync.add(conversationId);
                } else {
                    replay.addAll(messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(
                            conversationId, clientSeq, PageRequest.of(0, REPLAY_LIMIT)));
                }
            }
        });
        return new StreamGrant(user, new LinkedHashSet<>(readable.keySet()), expiry(now), replay, resync);
    }

    private Map<UUID, Conversation> readable(String orgKey, List<ReadMarker> followed) {
        Map<UUID, Conversation> readable = new HashMap<>();
        conversations.findAllById(followed.stream().map(ReadMarker::getConversationId).toList()).stream()
                .filter(conversation -> orgKey.equals(conversation.getOrgKey()))
                .forEach(conversation -> {
                    try {
                        if (access.of(conversation).canRead()) {
                            readable.put(conversation.getId(), conversation);
                        }
                    } catch (ConversationNotFoundException e) {
                        // Its definition was withdrawn or its subject is gone: nothing to push.
                    }
                });
        return readable;
    }

    private Instant expiry(Instant now) {
        Instant ceiling = now.plus(maxAge);
        return principal.tokenExpiresAt().filter(expiry -> expiry.isBefore(ceiling)).orElse(ceiling);
    }
}
