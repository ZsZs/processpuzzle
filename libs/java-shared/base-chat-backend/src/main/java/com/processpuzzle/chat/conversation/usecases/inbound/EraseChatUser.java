package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.common.ChatMetrics;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases one user's chat data on request — the right to erasure. What they wrote stays in the conversation, so other
 * people's conversations still make sense, but it no longer says who or what: the messages' author becomes
 * {@value #ERASED_AUTHOR}, their name is dropped and their text becomes {@value #ERASED_BODY}; the conversations they
 * opened lose their creator; their read markers are deleted.
 *
 * <p>Allowed for the user themself and for whoever administers the organization. There is no undo. The platform has
 * no "user deleted" event to react to, so this is called explicitly — by the user, or by an administrator who has
 * deleted the account.
 */
@Service
@Transactional
public class EraseChatUser {

    private static final Logger LOG = LoggerFactory.getLogger(EraseChatUser.class);

    /** What stands in for the author of an erased message; no identity provider subject looks like it. */
    public static final String ERASED_AUTHOR = "erased";
    /** What stands in for the text of an erased message. */
    public static final String ERASED_BODY = "[removed]";

    /** What was anonymized or deleted. */
    public record Result(int messages, int conversations, int readMarkers) {
    }

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final ReadMarkerRepository markers;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;
    private final ChatMetrics metrics;

    public EraseChatUser(MessageRepository messages, ConversationRepository conversations, ReadMarkerRepository markers,
                         OrganizationGuard guard, CurrentPrincipal principal, ChatMetrics metrics) {
        this.messages = messages;
        this.conversations = conversations;
        this.markers = markers;
        this.guard = guard;
        this.principal = principal;
        this.metrics = metrics;
    }

    public Result erase(String orgKey, String userSub) {
        guard.requireAccess(orgKey);
        if (userSub == null || userSub.isBlank() || ERASED_AUTHOR.equals(userSub)) {
            throw new InvalidChatRequestException("A user is required");
        }
        String caller = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Erasing needs a signed-in user"));
        if (!caller.equals(userSub)) {
            guard.requireAdministration(orgKey);
        }
        int erasedMessages = messages.anonymizeAuthor(orgKey, userSub, ERASED_AUTHOR, ERASED_BODY, Instant.now());
        int erasedConversations = conversations.anonymizeCreator(orgKey, userSub, ERASED_AUTHOR);
        int erasedMarkers = markers.deleteByUser(orgKey, userSub);
        metrics.userErased();
        // The subject of the user is not logged: the log is not meant to outlive the erasure it records.
        LOG.info("Chat data of a user of '{}' erased: {} message(s), {} conversation(s), {} read marker(s).", orgKey,
                erasedMessages, erasedConversations, erasedMarkers);
        return new Result(erasedMessages, erasedConversations, erasedMarkers);
    }
}
