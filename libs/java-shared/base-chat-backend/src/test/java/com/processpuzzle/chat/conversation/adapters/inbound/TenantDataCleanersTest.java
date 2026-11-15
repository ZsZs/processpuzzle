package com.processpuzzle.chat.conversation.adapters.inbound;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.definition.adapters.inbound.ChatDefinitionTenantDataCleaner;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;

/** Erasing a tenant removes its messages, then its conversations, then its definitions — nothing of another's. */
class TenantDataCleanersTest {

    @Test
    void deletesMarkersAndMessagesBeforeConversations() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        ReadMarkerRepository markers = mock(ReadMarkerRepository.class);

        new ConversationTenantDataCleaner(conversations, messages, markers).onOrganizationDeleted(new OrganizationDeletedEvent("org-1"));

        var order = inOrder(markers, messages, conversations);
        order.verify(markers).eraseByOrgKey("org-1");
        order.verify(messages).eraseByOrgKey("org-1");
        order.verify(conversations).eraseByOrgKey("org-1");
    }

    @Test
    void deletesTheDefinitionsOfTheTenant() {
        ChatDefinitionRepository definitions = mock(ChatDefinitionRepository.class);

        new ChatDefinitionTenantDataCleaner(definitions).onOrganizationDeleted(new OrganizationDeletedEvent("org-1"));

        verify(definitions).eraseByOrgKey("org-1");
    }
}
