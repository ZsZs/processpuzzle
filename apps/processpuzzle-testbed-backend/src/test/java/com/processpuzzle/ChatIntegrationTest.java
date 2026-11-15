package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.adapters.inbound.ChatStreamRegistry;
import com.processpuzzle.chat.conversation.adapters.inbound.StreamSink;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationConflictException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotWritableException;
import com.processpuzzle.chat.conversation.usecases.inbound.FindConversations;
import com.processpuzzle.chat.conversation.usecases.inbound.GetConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ListMessages;
import com.processpuzzle.chat.conversation.usecases.inbound.ListUnread;
import com.processpuzzle.chat.conversation.usecases.inbound.MarkRead;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenChatStream;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.PostMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.Unfollow;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.security.SecurityTestTokens;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.state.adapter.inbound.StateMachineReleaseParticipant;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Chat through the real platform: an entity instance of base-entity is the subject, base-state supplies its
 * state, and who may do what comes from the realm roles in the token and the released chat definitions.
 *
 * <p>In an organization of its own, with a trimmed {@code order}, its state machine, and chat definitions of
 * the shapes the rules turn on: open to every member, split by role, and bound to the order's state.
 */
@SpringBootTest
@RecordApplicationEvents
class ChatIntegrationTest {

    private static final String ORG = "chat-integration";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: order
                name: Order
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: orderNumber, name: 'Order #', displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - code: status
                    name: Status
                    displayOrder: 2
                    valueKind: ENUM
                    formControlType: DROPDOWN
                    enumValues: [ DRAFT, CONFIRMED ]
            """;

    private static final String STATE_MACHINES = """
            stateMachines:
              - entityName: order
                name: Order State Machine
                stateAttributeKey: status
                initialStateKey: DRAFT
                states:
                  - { key: DRAFT, name: Draft, isFinal: false, isLocked: false }
                  - { key: CONFIRMED, name: Confirmed, isFinal: false, isLocked: false }
                transitions:
                  - { key: confirm, name: Confirm, sourceStateKey: DRAFT, targetStateKey: CONFIRMED, triggerKey: confirm }
            """;

    private static final String CHATS = """
            chat-definitions:
              - key: order-discussion
                name: Order discussion
                subjectType: ENTITY
                subjectDefinition: order
              - key: order-crew
                name: Crew chat
                subjectType: ENTITY
                subjectDefinition: order
                readRoles: [ sailor, captain ]
                writeRoles: [ captain ]
              - key: order-draft-chat
                name: Draft-time chat
                subjectType: ENTITY
                subjectDefinition: order
                lifecycle: STATE_BOUND
                writableStates: [ DRAFT ]
              - key: order-topics
                name: Topics
                subjectType: ENTITY
                subjectDefinition: order
                multiplicity: MANY
            """;

    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private ImportStateMachineDefinitions importStateMachines;
    @Autowired private StateMachineReleaseParticipant stateRelease;
    @Autowired private ImportChatDefinitions importChats;
    @Autowired private CreateEntityInstanceUseCase createEntity;
    @Autowired private FireStateTransition fireTransition;
    @Autowired private EntityObjectAccess entities;
    @Autowired private OpenConversation open;
    @Autowired private PostMessage post;
    @Autowired private ListMessages list;
    @Autowired private GetConversation get;
    @Autowired private FindConversations find;
    @Autowired private ApplicationEvents events;
    @Autowired private MarkRead markRead;
    @Autowired private Unfollow unfollow;
    @Autowired private ListUnread listUnread;
    @Autowired private OpenChatStream openStream;
    @Autowired private ChatStreamRegistry registry;

    private String orderId;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, stream(ENTITIES)).errors()).isEmpty();
        entityRelease.release(ORG);
        assertThat(importStateMachines.execute(ORG, stream(STATE_MACHINES)).errors()).isEmpty();
        stateRelease.release(ORG);
        assertThat(importChats.execute(ORG, stream(CHATS), true).errors()).isEmpty();
        orderId = createEntity.create(ORG, "order", Map.of("orderNumber", "ORD-" + UUID.randomUUID())).getId().toString();
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
    }

    @AfterEach
    void signOut() {
        SecurityTestTokens.clear();
    }

    @Test
    void aMemberOpensTheOrdersChatPostsAndReadsItBackNewestFirst() {
        ConversationView opened = open.open(ORG, "order-discussion", orderId, null);

        assertThat(opened.conversation().getSubjectId()).isEqualTo(orderId);
        assertThat(opened.conversation().getSubjectDefinition()).isEqualTo("order");
        assertThat(opened.conversation().getStatus()).isEqualTo(ConversationStatus.OPEN);
        assertThat(opened.access().canWrite()).isTrue();

        UUID id = opened.conversation().getId();
        UUID firstClientId = UUID.randomUUID();
        PostedMessage first = post.post(ORG, id, firstClientId, "Is the paint here?");
        post.post(ORG, id, UUID.randomUUID(), "Yes, since Monday.");
        post.post(ORG, id, UUID.randomUUID(), "Thanks!");

        assertThat(first.message().getSeq()).isEqualTo(1);
        assertThat(first.message().getAuthorSub()).isEqualTo("sub-ann");
        assertThat(list.list(ORG, id, null, null)).extracting(Message::getBody)
                .containsExactly("Thanks!", "Yes, since Monday.", "Is the paint here?");
        assertThat(list.list(ORG, id, 3L, 1)).extracting(Message::getSeq).containsExactly(2L);
        assertThat(get.get(ORG, id).conversation().getLastSeq()).isEqualTo(3);

        PostedMessage replay = post.post(ORG, id, firstClientId, "Is the paint here?");
        assertThat(replay.created()).isFalse();
        assertThat(replay.message().getSeq()).isEqualTo(1);
        assertThat(get.get(ORG, id).conversation().getLastSeq()).isEqualTo(3);

        assertThat(find.find(ORG, SubjectType.ENTITY, orderId, "order", null))
                .extracting(view -> view.conversation().getId()).containsExactly(id);
    }

    @Test
    void openingAnnouncesTheConversationAsAPlatformFact() {
        ConversationView opened = open.open(ORG, "order-discussion", orderId, null);

        assertThat(events.stream(PlatformEvent.class))
                .filteredOn(event -> "chat-conversation".equals(event.subjectType()))
                .singleElement().satisfies(event -> {
                    assertThat(event.orgKey()).isEqualTo(ORG);
                    assertThat(event.subjectId()).isEqualTo(opened.conversation().getId().toString());
                    assertThat(event.payload()).containsEntry("subjectId", orderId).containsEntry("definitionKey", "order-discussion");
                });
    }

    @Test
    void theSecondConversationOfASingleChatIsAConflict() {
        open.open(ORG, "order-discussion", orderId, null);

        assertThatThrownBy(() -> open.open(ORG, "order-discussion", orderId, null)).isInstanceOf(ConversationConflictException.class);
    }

    @Test
    void aManyChatOpensAnyNumberOfTitledTopics() {
        open.open(ORG, "order-topics", orderId, "Paint");
        open.open(ORG, "order-topics", orderId, "Delivery");

        assertThat(find.find(ORG, SubjectType.ENTITY, orderId, null, "order-topics"))
                .extracting(view -> view.conversation().getTitle()).containsExactly("Paint", "Delivery");
    }

    @Test
    void aSubjectThatDoesNotExistHasNoChat() {
        assertThatThrownBy(() -> open.open(ORG, "order-discussion", UUID.randomUUID().toString(), null))
                .isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> open.open(ORG, "order-discussion", "not-a-uuid", null))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void theRealmRolesInTheTokenDecideWhoReadsAndWhoWrites() {
        SecurityTestTokens.authenticateAs(ORG, "sub-cap", "captain");
        UUID id = open.open(ORG, "order-crew", orderId, null).conversation().getId();
        post.post(ORG, id, UUID.randomUUID(), "All hands on deck");

        SecurityTestTokens.authenticateAs(ORG, "sub-sailor", "sailor");
        assertThat(get.get(ORG, id).access().canWrite()).isFalse();
        assertThat(list.list(ORG, id, null, null)).hasSize(1);
        assertThatThrownBy(() -> post.post(ORG, id, UUID.randomUUID(), "Aye"))
                .isInstanceOf(ChatAccessDeniedException.class);

        SecurityTestTokens.authenticateAs(ORG, "sub-passenger", "passenger");
        assertThatThrownBy(() -> get.get(ORG, id)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> list.list(ORG, id, null, null)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> post.post(ORG, id, UUID.randomUUID(), "Hello?")).isInstanceOf(ConversationNotFoundException.class);
        assertThat(find.find(ORG, SubjectType.ENTITY, orderId, null, "order-crew")).isEmpty();
    }

    @Test
    void aStateBoundChatStopsAcceptingMessagesWhenTheOrderLeavesItsWritableState() {
        UUID id = open.open(ORG, "order-draft-chat", orderId, null).conversation().getId();
        post.post(ORG, id, UUID.randomUUID(), "While it is a draft");

        fireTransition.execute(ORG, "order", UUID.fromString(orderId), "confirm", Map.of(), currentVersion());

        assertThat(get.get(ORG, id).access().canWrite()).isFalse();
        assertThatThrownBy(() -> post.post(ORG, id, UUID.randomUUID(), "Too late"))
                .isInstanceOf(ConversationNotWritableException.class);
        assertThat(list.list(ORG, id, null, null)).extracting(Message::getBody).containsExactly("While it is a draft");
    }

    // --- following, unread and the live stream

    /** What a connection was sent, in order. */
    private static final class RecordingSink implements StreamSink {
        private final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile boolean completed;

        @Override
        public void send(String id, String event, Object data) {
            events.add(event + (id == null ? "" : "@" + id));
        }

        @Override
        public void comment(String text) {
            // heartbeats are not interesting here
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    private RecordingSink connect(String user, String... roles) {
        SecurityTestTokens.authenticateAs(ORG, user, roles);
        RecordingSink sink = new RecordingSink();
        registry.register(ORG, openStream.open(ORG, Map.of()), sink);
        return sink;
    }

    @Test
    void unreadCountsWhatOthersPostAndClearsWhenReadAndUnfollowingDropsTheConversation() {
        UUID id = open.open(ORG, "order-discussion", orderId, null).conversation().getId();
        SecurityTestTokens.authenticateAs(ORG, "sub-bob");
        post.post(ORG, id, UUID.randomUUID(), "First");
        post.post(ORG, id, UUID.randomUUID(), "Second");

        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        assertThat(listUnread.list(ORG)).singleElement().satisfies(view -> {
            assertThat(view.conversation().getId()).isEqualTo(id);
            assertThat(view.unread()).isEqualTo(2);
        });
        assertThat(get.get(ORG, id).unread()).isEqualTo(2);

        SecurityTestTokens.authenticateAs(ORG, "sub-bob");
        assertThat(listUnread.list(ORG)).singleElement().satisfies(view -> assertThat(view.unread())
                .as("a poster who was up to date has read what they wrote").isZero());

        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        assertThat(markRead.markRead(ORG, id, 99).unread()).isZero();
        assertThat(get.get(ORG, id).lastReadSeq()).isEqualTo(2);

        unfollow.unfollow(ORG, id);
        assertThat(listUnread.list(ORG)).isEmpty();
        assertThat(get.get(ORG, id).following()).isFalse();
    }

    @Test
    void aPostReachesTheFollowersOfTheConversationAfterItCommitsAndNobodyElse() {
        UUID id = open.open(ORG, "order-discussion", orderId, null).conversation().getId();
        RecordingSink ann = connect("sub-ann");
        RecordingSink bob = connect("sub-bob");
        RecordingSink carol = connect("sub-carol");
        assertThat(ann.events).containsExactly("stream.ready");

        SecurityTestTokens.authenticateAs(ORG, "sub-bob");
        post.post(ORG, id, UUID.randomUUID(), "Hello");

        assertThat(ann.events).as("opened it, so follows it").containsExactly("stream.ready", "message.posted@" + id + ":1");
        assertThat(bob.events).as("not following when the stream opened; posting followed, so their own stream gets it too")
                .containsExactly("stream.ready", "read.updated", "message.posted@" + id + ":1");
        assertThat(carol.events).containsExactly("stream.ready");

        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        markRead.markRead(ORG, id, 1);
        assertThat(ann.events).endsWith("read.updated");
    }

    @Test
    void aPostThatIsRefusedIsNeverAnnounced() {
        SecurityTestTokens.authenticateAs(ORG, "sub-capt", "captain");
        UUID id = open.open(ORG, "order-crew", orderId, null).conversation().getId();
        RecordingSink captain = connect("sub-capt", "captain");

        SecurityTestTokens.authenticateAs(ORG, "sub-passenger", "passenger");
        assertThatThrownBy(() -> post.post(ORG, id, UUID.randomUUID(), "Let me in")).isInstanceOf(ConversationNotFoundException.class);

        assertThat(captain.events).containsExactly("stream.ready");
    }

    @Test
    void aStreamOpenedAfterARoleIsLostNoLongerCarriesTheConversation() {
        SecurityTestTokens.authenticateAs(ORG, "sub-sailor", "sailor");
        UUID id = open.open(ORG, "order-crew", orderId, null).conversation().getId();
        assertThat(openStream.open(ORG, Map.of()).conversations()).containsExactly(id);

        SecurityTestTokens.authenticateAs(ORG, "sub-sailor", "passenger");
        StreamGrant grant = openStream.open(ORG, Map.of(id, 0L));

        assertThat(grant.conversations()).isEmpty();
        assertThat(grant.replay()).isEmpty();
        assertThatThrownBy(() -> get.get(ORG, id)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void aReconnectingClientIsGivenWhatItMissed() {
        UUID id = open.open(ORG, "order-discussion", orderId, null).conversation().getId();
        post.post(ORG, id, UUID.randomUUID(), "One");
        post.post(ORG, id, UUID.randomUUID(), "Two");
        post.post(ORG, id, UUID.randomUUID(), "Three");

        StreamGrant grant = openStream.open(ORG, Map.of(id, 1L));

        assertThat(grant.replay()).extracting(Message::getBody).containsExactly("Two", "Three");
        assertThat(grant.resync()).isEmpty();
        assertThat(grant.expiresAt()).isAfter(java.time.Instant.now()).isBefore(java.time.Instant.now().plusSeconds(15 * 60 + 1));
    }

    private long currentVersion() {
        return entities.find(ORG, "order", UUID.fromString(orderId)).version();
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
