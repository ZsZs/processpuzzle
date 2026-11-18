package com.processpuzzle.chat.conversation.domain;

import static com.processpuzzle.chat.conversation.ConversationFixtures.DEFINITION_KEY;
import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.chat.definition.domain.SubjectType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The mapping binds, and the constraints and statements the posting rules rely on do what they claim. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-conversation-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.chat.conversation.domain")
@EnableJpaRepositories("com.processpuzzle.chat.conversation.domain")
class ConversationPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ConversationRepository conversations;
    @Autowired
    private MessageRepository messages;
    @Autowired
    private EntityManager entityManager;

    private Conversation saved() {
        Conversation conversation = conversation();
        conversation.setId(null);
        return conversations.saveAndFlush(conversation);
    }

    private void refresh() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void roundTripsAConversationAndGeneratesItsId() {
        Conversation stored = saved();
        refresh();

        Conversation reloaded = conversations.findByIdAndOrgKey(stored.getId(), ORG).orElseThrow();

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getSubjectType()).isEqualTo(SubjectType.ENTITY);
        assertThat(reloaded.getSubjectDefinition()).isEqualTo("order");
        assertThat(reloaded.getStatus()).isEqualTo(ConversationStatus.OPEN);
        assertThat(reloaded.getLastSeq()).isZero();
        assertThat(reloaded.getCreatedBy()).isEqualTo("sub-opener");
        assertThat(reloaded.getCreatedByName()).isEqualTo("Olga Opener");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void aConversationOfAnotherOrganizationIsNotFound() {
        Conversation stored = saved();

        assertThat(conversations.findByIdAndOrgKey(stored.getId(), "org-2")).isEmpty();
    }

    @Test
    void aSingleDefinitionsBindingIsUniquePerOrganizationInTheDatabase() {
        saved();
        Conversation second = conversation();
        second.setId(null);

        assertThatThrownBy(() -> conversations.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSubjectCannotHaveTwoTopicsOfOneTitleButAnotherSubjectCan() {
        for (String subject : List.of("o-1", "o-2")) {
            Conversation topic = conversation();
            topic.setId(null);
            topic.setSubjectId(subject);
            topic.setTitle("Delivery");
            topic.setBindingKey(Conversation.bindingKeyOf(DEFINITION_KEY, subject, "Delivery"));
            conversations.saveAndFlush(topic);
        }
        Conversation again = conversation();
        again.setId(null);
        again.setSubjectId("o-1");
        again.setTitle(" delivery");
        again.setBindingKey(Conversation.bindingKeyOf(DEFINITION_KEY, "o-1", " delivery"));

        assertThatThrownBy(() -> conversations.saveAndFlush(again)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameBindingInAnotherOrganizationIsFineAndTopicsWithoutOneRepeat() {
        saved();
        Conversation elsewhere = conversation();
        elsewhere.setId(null);
        elsewhere.setOrgKey("org-2");
        conversations.saveAndFlush(elsewhere);

        for (String title : List.of("Protest 1", "Protest 2")) {
            Conversation topic = conversation();
            topic.setId(null);
            topic.setBindingKey(null);
            topic.setTitle(title);
            conversations.saveAndFlush(topic);
        }

        assertThat(conversations.existsByOrgKeyAndBindingKey(ORG, Conversation.bindingKeyOf(DEFINITION_KEY, conversation().getSubjectId()))).isTrue();
        assertThat(conversations.existsByOrgKeyAndBindingKey(ORG, "unknown|x")).isFalse();
        assertThat(conversations.findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(ORG, SubjectType.ENTITY, conversation().getSubjectId()))
                .hasSize(3);
    }

    @Test
    void numbersAreTakenOneAtATimeAndReadBackFromTheDatabase() {
        Conversation stored = saved();
        UUID id = stored.getId();

        assertThat(conversations.lockIfStatus(id, ORG, ConversationStatus.OPEN)).isEqualTo(1);
        assertThat(conversations.advanceSeq(id, ORG)).isEqualTo(1);
        assertThat(conversations.lastSeqOf(id)).isEqualTo(1);
        conversations.advanceSeq(id, ORG);
        assertThat(conversations.lastSeqOf(id)).isEqualTo(2);

        refresh();
        assertThat(conversations.findByIdAndOrgKey(id, ORG).orElseThrow().getLastSeq()).isEqualTo(2);
    }

    @Test
    void theLockIsOnlyGrantedToAConversationWithTheAskedStatusAndOrganization() {
        Conversation stored = saved();
        stored.setStatus(ConversationStatus.CLOSED);
        conversations.saveAndFlush(stored);

        assertThat(conversations.lockIfStatus(stored.getId(), ORG, ConversationStatus.OPEN)).isZero();
        assertThat(conversations.lockIfStatus(stored.getId(), ORG, ConversationStatus.CLOSED)).isEqualTo(1);
        assertThat(conversations.lockIfStatus(stored.getId(), "org-2", ConversationStatus.CLOSED)).isZero();
        assertThat(conversations.lockIfStatus(UUID.randomUUID(), ORG, ConversationStatus.OPEN)).isZero();
    }

    @Test
    void savingAStaleCopyOfTheEntityNeverWritesAnOldNumberBack() {
        Conversation stored = saved();
        refresh();
        Conversation stale = conversations.findByIdAndOrgKey(stored.getId(), ORG).orElseThrow();

        conversations.advanceSeq(stored.getId(), ORG);
        conversations.advanceSeq(stored.getId(), ORG);
        stale.setStatus(ConversationStatus.CLOSED);
        conversations.saveAndFlush(stale);
        refresh();

        Conversation reloaded = conversations.findByIdAndOrgKey(stored.getId(), ORG).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ConversationStatus.CLOSED);
        assertThat(reloaded.getLastSeq()).isEqualTo(2);
    }

    @Test
    void pagesHistoryNewestFirstBelowACursor() {
        Conversation stored = saved();
        for (long seq = 1; seq <= 5; seq++) {
            messages.save(message(stored, seq));
        }
        messages.flush();

        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(stored.getId(), Long.MAX_VALUE, PageRequest.of(0, 3)))
                .extracting(Message::getSeq).containsExactly(5L, 4L, 3L);
        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(stored.getId(), 3, PageRequest.of(0, 3)))
                .extracting(Message::getSeq).containsExactly(2L, 1L);
        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(stored.getId(), 1, PageRequest.of(0, 3))).isEmpty();
        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(UUID.randomUUID(), Long.MAX_VALUE, PageRequest.of(0, 3))).isEmpty();
    }

    @Test
    void aMessageRoundTripsAndIsFoundByItsClientId() {
        Conversation stored = saved();
        Message message = message(stored, 1);
        messages.saveAndFlush(message);
        refresh();

        Message found = messages.findByConversationIdAndAuthorSubAndClientMessageId(
                stored.getId(), "sub-author", message.getClientMessageId()).orElseThrow();

        assertThat(found.getSeq()).isEqualTo(1);
        assertThat(found.getBody()).isEqualTo("message 1");
        assertThat(found.getKind()).isEqualTo(MessageKind.TEXT);
        assertThat(found.getAuthorDisplayName()).isEqualTo("Ann Author");
        assertThat(found.getDeletedAt()).isNull();
        assertThat(messages.findByConversationIdAndAuthorSubAndClientMessageId(stored.getId(), "someone-else", message.getClientMessageId())).isEmpty();
    }

    @Test
    void aClientIdCannotBeStoredTwiceForOneAuthorInOneConversation() {
        Conversation stored = saved();
        Message first = message(stored, 1);
        messages.saveAndFlush(first);
        Message duplicate = message(stored, 2);
        duplicate.setClientMessageId(first.getClientMessageId());

        assertThatThrownBy(() -> messages.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void erasesOneTenantsMessagesAndConversationsAndLeavesTheOthers() {
        Conversation mine = saved();
        messages.save(message(mine, 1));
        Conversation theirs = conversation();
        theirs.setId(null);
        theirs.setOrgKey("org-2");
        theirs = conversations.saveAndFlush(theirs);
        messages.saveAndFlush(message(theirs, 1));

        assertThat(messages.eraseByOrgKey(ORG)).isEqualTo(1);
        assertThat(conversations.eraseByOrgKey(ORG)).isEqualTo(1);
        refresh();

        assertThat(conversations.findByIdAndOrgKey(mine.getId(), ORG)).isEmpty();
        assertThat(conversations.findByIdAndOrgKey(theirs.getId(), "org-2")).isPresent();
        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(theirs.getId(), Long.MAX_VALUE, PageRequest.of(0, 5))).hasSize(1);
    }
}
