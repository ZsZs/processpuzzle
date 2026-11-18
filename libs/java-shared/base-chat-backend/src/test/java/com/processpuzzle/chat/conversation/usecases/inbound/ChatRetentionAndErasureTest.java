package com.processpuzzle.chat.conversation.usecases.inbound;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.common.ChatMetrics;
import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retention deletes what has been idle for longer than its definition keeps it, and erasure takes a user's name and
 * words out of what stays — against the real statements, on a database.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-retention;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.chat.conversation.domain", "com.processpuzzle.chat.definition.domain"})
@EnableJpaRepositories({"com.processpuzzle.chat.conversation.domain", "com.processpuzzle.chat.definition.domain"})
class ChatRetentionAndErasureTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");

    @Autowired
    private ConversationRepository conversations;
    @Autowired
    private MessageRepository messages;
    @Autowired
    private ReadMarkerRepository markers;
    @Autowired
    private ChatDefinitionRepository definitions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactions;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ChatMetrics metrics;
    private ChatRetention retention;

    @BeforeEach
    void setUp() {
        metrics = new ChatMetrics(meters);
        retention = new ChatRetention(definitions, conversations, messages, markers, new TransactionTemplate(transactions), metrics, true, 0);
    }

    private void releasedDefinition(String key, Integer retentionDays) {
        ChatDefinition definition = ChatDefinition.releasedCopyOf(ChatDefinitionTest.definition(key));
        definition.setRetentionDays(retentionDays);
        definitions.saveAndFlush(definition);
    }

    /** A conversation of {@code definitionKey} whose newest message is {@code daysAgo} old (no message: it was opened then). */
    private Conversation conversationIdleFor(String definitionKey, long daysAgo, boolean withMessage) {
        Conversation conversation = conversation();
        conversation.setId(null);
        conversation.setDefinitionKey(definitionKey);
        conversation.setBindingKey(UUID.randomUUID().toString());
        conversation.setSubjectId(UUID.randomUUID().toString());
        Instant then = NOW.minus(Duration.ofDays(daysAgo));
        conversation.setCreatedAt(then.minus(Duration.ofDays(1)));
        Conversation saved = conversations.saveAndFlush(conversation);
        if (withMessage) {
            Message message = message(saved, 1);
            message.setCreatedAt(then);
            messages.saveAndFlush(message);
            ReadMarker marker = ReadMarker.builder().conversationId(saved.getId()).userSub("sub-reader").orgKey(ORG).lastReadSeq(1).updatedAt(then).build();
            markers.saveAndFlush(marker);
        }
        return saved;
    }

    private boolean exists(Conversation conversation) {
        entityManager.clear();
        return conversations.findByIdAndOrgKey(conversation.getId(), ORG).isPresent();
    }

    // --- retention

    @Test
    void deletesAConversationIdleLongerThanTheDefinitionKeepsItWithItsMessagesAndMarkers() {
        releasedDefinition("order-discussion", 30);
        Conversation idle = conversationIdleFor("order-discussion", 31, true);
        Conversation active = conversationIdleFor("order-discussion", 29, true);

        int deleted = retention.purgeIdle(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(idle)).isFalse();
        assertThat(exists(active)).isTrue();
        assertThat(messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(idle.getId(), 0, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(markers.findByConversationIdAndUserSub(idle.getId(), "sub-reader")).isEmpty();
        assertThat(markers.findByConversationIdAndUserSub(active.getId(), "sub-reader")).isPresent();
        assertThat(meters.get("chat.retention.conversations.deleted").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aConversationWithoutMessagesIsIdleFromItsOpening() {
        releasedDefinition("order-discussion", 30);
        Conversation empty = conversationIdleFor("order-discussion", 40, false);

        assertThat(retention.purgeIdle(NOW)).isEqualTo(1);
        assertThat(exists(empty)).isFalse();
    }

    @Test
    void aDefinitionWithoutItsOwnPeriodTakesTheDeploymentDefault() {
        releasedDefinition("order-discussion", null);
        Conversation old = conversationIdleFor("order-discussion", 31, true);
        Conversation recent = conversationIdleFor("order-discussion", 29, true);
        ChatRetention withDefault = new ChatRetention(definitions, conversations, messages, markers, new TransactionTemplate(transactions), metrics, true, 30);

        assertThat(withDefault.purgeIdle(NOW)).isEqualTo(1);
        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    void aDefinitionsOwnPeriodBeatsTheDeploymentDefault() {
        releasedDefinition("short", 7);
        Conversation idleTenDays = conversationIdleFor("short", 10, true);
        ChatRetention withDefault = new ChatRetention(definitions, conversations, messages, markers, new TransactionTemplate(transactions), metrics, true, 365);

        assertThat(withDefault.purgeIdle(NOW)).isEqualTo(1);
        assertThat(exists(idleTenDays)).isFalse();
    }

    @Test
    void noDeploymentDefaultMeansForEverWhenTheDefinitionSetsNothing() {
        releasedDefinition("order-discussion", null);
        ChatRetention none = new ChatRetention(definitions, conversations, messages, markers, new TransactionTemplate(transactions), metrics, true, 0);

        assertThat(none.periodOf(definitions.findByChannel(com.processpuzzle.core.definition.Channel.RELEASED).get(0))).isNull();
    }

    @Test
    void aDefinitionWithoutRetentionKeepsItsConversationsForEver() {
        releasedDefinition("order-discussion", null);
        Conversation old = conversationIdleFor("order-discussion", 4000, true);

        assertThat(retention.purgeIdle(NOW)).isZero();
        assertThat(exists(old)).isTrue();
    }

    @Test
    void eachDefinitionKeepsItsOwnPeriod() {
        releasedDefinition("short", 7);
        releasedDefinition("long", 365);
        Conversation shortOne = conversationIdleFor("short", 10, true);
        Conversation longOne = conversationIdleFor("long", 10, true);

        retention.purgeIdle(NOW);

        assertThat(exists(shortOne)).isFalse();
        assertThat(exists(longOne)).isTrue();
    }

    @Test
    void aConversationWhoseDefinitionIsNotReleasedHasNoPolicyAndStays() {
        ChatDefinition draftOnly = ChatDefinitionTest.definition("draft-only");
        draftOnly.setRetentionDays(1);
        definitions.saveAndFlush(draftOnly);
        Conversation old = conversationIdleFor("draft-only", 400, true);

        assertThat(retention.purgeIdle(NOW)).isZero();
        assertThat(exists(old)).isTrue();
    }

    @Test
    void worksOffABacklogInBatches() {
        releasedDefinition("order-discussion", 1);
        for (int i = 0; i < ChatRetention.BATCH + 5; i++) {
            conversationIdleFor("order-discussion", 5, false);
        }

        assertThat(retention.purgeIdle(NOW)).isEqualTo(ChatRetention.BATCH + 5);
    }

    @Test
    void theScheduledPassDoesNothingWhenSwitchedOff() {
        releasedDefinition("order-discussion", 1);
        Conversation old = conversationIdleFor("order-discussion", 50, true);
        ChatRetention off = new ChatRetention(definitions, conversations, messages, markers, new TransactionTemplate(transactions), metrics, false, 0);

        off.scheduledPass();

        assertThat(exists(old)).isTrue();
        assertThat(Optional.ofNullable(meters.find("chat.retention.conversations.deleted").counter())).isEmpty();
    }

    // --- erasure

    private EraseChatUser eraser(String caller, OrganizationGuard guard) {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.subject()).thenReturn(Optional.ofNullable(caller));
        return new EraseChatUser(messages, conversations, markers, guard, principal, metrics);
    }

    private Message messageBy(Conversation conversation, long seq, String author) {
        Message message = message(conversation, seq);
        message.setAuthorSub(author);
        message.setAuthorDisplayName(author + " Name");
        return messages.saveAndFlush(message);
    }

    @Test
    void erasingAUserAnonymizesWhatTheyWroteAndKeepsTheConversationReadable() {
        Conversation opened = conversationIdleFor("order-discussion", 1, false);
        messageBy(opened, 1, "sub-opener");
        messageBy(opened, 2, "sub-bob");
        markers.saveAndFlush(ReadMarker.builder().conversationId(opened.getId()).userSub("sub-opener").orgKey(ORG).lastReadSeq(2).updatedAt(NOW).build());
        entityManager.clear();

        EraseChatUser.Result result = eraser("sub-opener", mock(OrganizationGuard.class)).erase(ORG, "sub-opener");
        entityManager.clear();

        assertThat(result).isEqualTo(new EraseChatUser.Result(1, 1, 1));
        Message erased = messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(opened.getId(), 0, org.springframework.data.domain.PageRequest.of(0, 10)).get(0);
        assertThat(erased.getAuthorSub()).isEqualTo(EraseChatUser.ERASED_AUTHOR);
        assertThat(erased.getAuthorDisplayName()).isNull();
        assertThat(erased.getBody()).isEqualTo(EraseChatUser.ERASED_BODY);
        assertThat(erased.getDeletedAt()).isNotNull();
        assertThat(erased.getSeq()).isEqualTo(1);
        Message other = messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(opened.getId(), 0, org.springframework.data.domain.PageRequest.of(0, 10)).get(1);
        assertThat(other.getAuthorSub()).isEqualTo("sub-bob");
        assertThat(other.getBody()).isEqualTo("message 2");
        Conversation conversation = conversations.findByIdAndOrgKey(opened.getId(), ORG).orElseThrow();
        assertThat(conversation.getCreatedBy()).isEqualTo(EraseChatUser.ERASED_AUTHOR);
        assertThat(conversation.getCreatedByName()).isNull();
        assertThat(markers.findByConversationIdAndUserSub(opened.getId(), "sub-opener")).isEmpty();
        assertThat(meters.get("chat.users.erased").counter().count()).isEqualTo(1.0);
    }

    @Test
    void anotherOrganizationsMessagesOfTheSameSubjectAreLeftAlone() {
        Conversation mine = conversationIdleFor("order-discussion", 1, false);
        messageBy(mine, 1, "sub-ann");
        Conversation theirs = conversation();
        theirs.setId(null);
        theirs.setOrgKey("org-2");
        theirs.setBindingKey(UUID.randomUUID().toString());
        theirs.setSubjectId(UUID.randomUUID().toString());
        Conversation other = conversations.saveAndFlush(theirs);
        Message foreign = message(other, 1);
        foreign.setOrgKey("org-2");
        foreign.setAuthorSub("sub-ann");
        messages.saveAndFlush(foreign);

        eraser("sub-ann", mock(OrganizationGuard.class)).erase(ORG, "sub-ann");
        entityManager.clear();

        assertThat(messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(other.getId(), 0, org.springframework.data.domain.PageRequest.of(0, 10)).get(0).getAuthorSub())
                .isEqualTo("sub-ann");
    }

    @Test
    void anAdministratorMayEraseSomeoneElseAndAnOrdinaryUserMayNot() {
        OrganizationGuard guard = mock(OrganizationGuard.class);
        eraser("sub-admin", guard).erase(ORG, "sub-ann");
        verify(guard).requireAdministration(ORG);

        OrganizationGuard refusing = mock(OrganizationGuard.class);
        doThrow(new OrganizationAccessDeniedException(ORG)).when(refusing).requireAdministration(ORG);
        EraseChatUser ordinary = eraser("sub-bob", refusing);
        assertThatThrownBy(() -> ordinary.erase(ORG, "sub-ann")).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void aUserMayEraseThemselvesWithoutAdministrationRights() {
        OrganizationGuard guard = mock(OrganizationGuard.class);

        eraser("sub-ann", guard).erase(ORG, "sub-ann");

        verify(guard, never()).requireAdministration(ORG);
    }

    @Test
    void needsASignedInCallerAndAUser() {
        EraseChatUser anonymous = eraser(null, mock(OrganizationGuard.class));
        assertThatThrownBy(() -> anonymous.erase(ORG, "sub-ann")).isInstanceOf(ChatAccessDeniedException.class);

        EraseChatUser signedIn = eraser("sub-ann", mock(OrganizationGuard.class));
        assertThatThrownBy(() -> signedIn.erase(ORG, " ")).isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> signedIn.erase(ORG, EraseChatUser.ERASED_AUTHOR)).isInstanceOf(InvalidChatRequestException.class);
    }

    @Test
    void erasingAUserWhoWroteNothingIsNotAnError() {
        assertThat(eraser("sub-ann", mock(OrganizationGuard.class)).erase(ORG, "sub-ann")).isEqualTo(new EraseChatUser.Result(0, 0, 0));
    }
}
