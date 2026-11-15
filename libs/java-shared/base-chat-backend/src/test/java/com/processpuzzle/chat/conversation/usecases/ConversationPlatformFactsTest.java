package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.SUBJECT_ID;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.chat.common.ChatPlatformSubjectConfiguration;
import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ConversationPlatformFacts;
import com.processpuzzle.core.definition.PlatformSubject;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ConversationPlatformFactsTest {

    @Test
    void publishesTheOpeningAsACreatedFactAboutTheConversation() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Conversation conversation = conversation();
        conversation.setTitle("Protest of race 3");

        new ConversationPlatformFacts(events).opened(conversation);

        ArgumentCaptor<PlatformEvent> published = ArgumentCaptor.forClass(PlatformEvent.class);
        verify(events).publishEvent(published.capture());
        PlatformEvent fact = published.getValue();
        assertThat(fact.orgKey()).isEqualTo(ORG);
        assertThat(fact.subjectType()).isEqualTo("chat-conversation");
        assertThat(fact.subjectId()).isEqualTo(conversation.getId().toString());
        assertThat(fact.action()).isEqualTo(PlatformEventAction.CREATED);
        assertThat(fact.payload()).containsEntry("definitionKey", "order-discussion").containsEntry("subjectType", "ENTITY")
                .containsEntry("subjectDefinition", "order").containsEntry("subjectId", SUBJECT_ID)
                .containsEntry("title", "Protest of race 3").containsOnlyKeys(ConversationPlatformFacts.FACT_ATTRIBUTES);
        assertThat(fact.occurredAt()).isNotNull();
    }

    @Test
    void declaresTheSubjectSoTheEventCatalogCanNameIt() {
        PlatformSubject subject = new ChatPlatformSubjectConfiguration().chatConversationPlatformSubject();

        assertThat(subject.subjectType()).isEqualTo("chat-conversation");
        assertThat(subject.attributes()).containsExactlyInAnyOrderElementsOf(ConversationPlatformFacts.FACT_ATTRIBUTES);
    }
}
