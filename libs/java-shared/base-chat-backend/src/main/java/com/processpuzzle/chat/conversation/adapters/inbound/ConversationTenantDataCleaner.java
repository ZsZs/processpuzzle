package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the conversations, messages and read markers of an erased tenant.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, so the delete joins the erasing transaction and a tenant is either erased
 * in every feature or in none. An {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 * Markers and messages go first: they carry the tenant's key themselves, so no join is needed.
 *
 * <p>Named, because other features declare a {@code TenantDataCleaner} too, and an application that
 * assembles several of them would otherwise have two beans of one name.
 */
@Component("chatConversationTenantDataCleaner")
public class ConversationTenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationTenantDataCleaner.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ReadMarkerRepository markers;

    public ConversationTenantDataCleaner(ConversationRepository conversations, MessageRepository messages,
                                         ReadMarkerRepository markers) {
        this.conversations = conversations;
        this.messages = messages;
        this.markers = markers;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        int erasedMarkers = markers.eraseByOrgKey(orgKey);
        int erasedMessages = messages.eraseByOrgKey(orgKey);
        int erasedConversations = conversations.eraseByOrgKey(orgKey);

        LOG.info("Organization '{}' erased: {} read marker(s), {} chat message(s) and {} conversation(s) deleted.",
                orgKey, erasedMarkers, erasedMessages, erasedConversations);
    }
}
