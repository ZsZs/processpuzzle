package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the chat definitions of an erased tenant, in the erasing transaction ({@code BEFORE_COMMIT}, as
 * every feature's cleaner — see {@code ConversationTenantDataCleaner}).
 */
@Component("chatDefinitionTenantDataCleaner")
public class ChatDefinitionTenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(ChatDefinitionTenantDataCleaner.class);

    private final ChatDefinitionRepository definitions;

    public ChatDefinitionTenantDataCleaner(ChatDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        int erased = definitions.eraseByOrgKey(event.orgKey());

        LOG.info("Organization '{}' erased: {} chat definition(s) deleted.", event.orgKey(), erased);
    }
}
