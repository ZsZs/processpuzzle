package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.common.ChatMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.events.MessagePostedEvent;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Who gets which event: only connections of the organization that follow the conversation, in order. */
class ChatStreamRegistryTest {

    private static final Instant FAR = Instant.now().plusSeconds(3600);

    /** Records what a connection was sent; can be made to fail like a client that went away. */
    static final class RecordingSink implements StreamSink {
        record Sent(String id, String event, Object data) {
        }

        final List<Sent> sent = new ArrayList<>();
        int comments;
        boolean completed;
        boolean failing;

        @Override
        public void send(String id, String event, Object data) throws IOException {
            if (failing) {
                throw new IOException("broken pipe");
            }
            sent.add(new Sent(id, event, data));
        }

        @Override
        public void comment(String text) throws IOException {
            if (failing) {
                throw new IOException("broken pipe");
            }
            comments++;
        }

        @Override
        public void complete() {
            completed = true;
        }

        List<String> events() {
            return sent.stream().map(Sent::event).toList();
        }
    }

    private ChatStreamRegistry registry;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        registry = new ChatStreamRegistry(new ConversationMapper(), new ChatMetrics((MeterRegistry) null), 25);
        conversation = conversation();
    }

    private StreamGrant grant(String user, Set<UUID> conversations, Instant expiresAt, List<Message> replay, Set<UUID> resync) {
        return new StreamGrant(user, conversations, expiresAt, replay, resync);
    }

    private RecordingSink connect(String org, String user, UUID... conversations) {
        RecordingSink sink = new RecordingSink();
        registry.register(org, grant(user, Set.of(conversations), FAR, List.of(), Set.of()), sink);
        return sink;
    }

    // --- opening

    @Test
    void anOpenedConnectionIsToldWhenItExpiresThenWhatItMissedThenWhatToReload() {
        RecordingSink sink = new RecordingSink();
        UUID stale = UUID.randomUUID();

        registry.register(ORG, grant("ann", Set.of(conversation.getId()), FAR,
                List.of(message(conversation, 4), message(conversation, 5)), Set.of(stale)), sink);

        assertThat(sink.events()).containsExactly("stream.ready", "message.posted", "message.posted", "resync");
        assertThat(sink.sent.get(0).data()).isEqualTo(new ChatStreamRegistry.Ready(FAR.toString(), 25));
        assertThat(sink.sent).extracting(RecordingSink.Sent::id).containsExactly(null, conversation.getId() + ":4", conversation.getId() + ":5", null);
        assertThat(sink.sent.get(3).data()).isEqualTo(new ChatStreamRegistry.Resync(stale));
        assertThat(registry.openConnections()).isEqualTo(1);
    }

    @Test
    void aConnectionThatFailsDuringTheInitialBurstIsNotKept() {
        RecordingSink sink = new RecordingSink();
        sink.failing = true;

        registry.register(ORG, grant("ann", Set.of(conversation.getId()), FAR, List.of(), Set.of()), sink);

        assertThat(registry.openConnections()).isZero();
        assertThat(sink.completed).isTrue();
    }

    // --- messages

    @Test
    void aPostedMessageGoesToEveryFollowerOfTheConversationInItsOrganizationOnly() {
        RecordingSink ann = connect(ORG, "ann", conversation.getId());
        RecordingSink bob = connect(ORG, "bob", conversation.getId());
        RecordingSink notFollowing = connect(ORG, "cora", UUID.randomUUID());
        RecordingSink otherOrg = connect("org-2", "dan", conversation.getId());

        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 3)));

        assertThat(ann.events()).containsExactly("stream.ready", "message.posted");
        assertThat(bob.events()).containsExactly("stream.ready", "message.posted");
        assertThat(notFollowing.events()).containsExactly("stream.ready");
        assertThat(otherOrg.events()).containsExactly("stream.ready");
        RecordingSink.Sent sent = ann.sent.get(1);
        assertThat(sent.id()).isEqualTo(conversation.getId() + ":3");
        ChatStreamRegistry.MessagePosted data = (ChatStreamRegistry.MessagePosted) sent.data();
        assertThat(data.conversationId()).isEqualTo(conversation.getId());
        assertThat(data.message().getSeq()).isEqualTo(3);
        assertThat(data.message().getBody()).isEqualTo("message 3");
    }

    @Test
    void aConnectionThatFailsToTakeAnEventIsDroppedAndTheOthersStillGetIt() {
        RecordingSink broken = connect(ORG, "ann", conversation.getId());
        RecordingSink healthy = connect(ORG, "bob", conversation.getId());
        broken.failing = true;

        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 1)));
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 2)));

        assertThat(broken.completed).isTrue();
        assertThat(healthy.events()).containsExactly("stream.ready", "message.posted", "message.posted");
        assertThat(registry.openConnections()).isEqualTo(1);
    }

    // --- markers

    @Test
    void aMarkerChangeGoesToTheUsersOwnConnectionsOnly() {
        RecordingSink annTab1 = connect(ORG, "ann", conversation.getId());
        RecordingSink annTab2 = connect(ORG, "ann", conversation.getId());
        RecordingSink bob = connect(ORG, "bob", conversation.getId());

        registry.onReadMarkerChanged(new ReadMarkerChangedEvent(ORG, "ann", conversation.getId(), 7, true));

        assertThat(annTab1.sent.get(1).event()).isEqualTo("read.updated");
        assertThat(annTab1.sent.get(1).data()).isEqualTo(new ChatStreamRegistry.ReadUpdated(conversation.getId(), 7, true));
        assertThat(annTab2.events()).containsExactly("stream.ready", "read.updated");
        assertThat(bob.events()).containsExactly("stream.ready");
    }

    @Test
    void followingAddsTheConversationToTheUsersOpenStreamsAndUnfollowingDropsIt() {
        RecordingSink ann = connect(ORG, "ann");
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 1)));
        assertThat(ann.events()).containsExactly("stream.ready");

        registry.onReadMarkerChanged(new ReadMarkerChangedEvent(ORG, "ann", conversation.getId(), 0, true));
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 2)));
        assertThat(ann.events()).containsExactly("stream.ready", "read.updated", "message.posted");

        registry.onReadMarkerChanged(new ReadMarkerChangedEvent(ORG, "ann", conversation.getId(), 0, false));
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 3)));
        assertThat(ann.events()).containsExactly("stream.ready", "read.updated", "message.posted", "read.updated");
    }

    // --- heartbeat and expiry

    @Test
    void theHeartbeatPingsEveryOpenConnection() {
        RecordingSink ann = connect(ORG, "ann", conversation.getId());
        RecordingSink bob = connect(ORG, "bob", conversation.getId());

        registry.tick(Instant.now());
        registry.tick(Instant.now());

        assertThat(ann.comments).isEqualTo(2);
        assertThat(bob.comments).isEqualTo(2);
    }

    @Test
    void aConnectionWhoseGrantHasExpiredIsEndedAndRemoved() {
        RecordingSink expiring = new RecordingSink();
        registry.register(ORG, grant("ann", Set.of(conversation.getId()), Instant.parse("2026-11-15T12:00:00Z"), List.of(), Set.of()), expiring);
        RecordingSink lasting = connect(ORG, "bob", conversation.getId());

        registry.tick(Instant.parse("2026-11-15T12:00:00Z"));

        assertThat(expiring.completed).isTrue();
        assertThat(expiring.comments).isZero();
        assertThat(lasting.completed).isFalse();
        assertThat(registry.openConnections()).isEqualTo(1);
    }

    @Test
    void aConnectionThatCannotTakeAPingIsFoundAndRemoved() {
        RecordingSink gone = connect(ORG, "ann", conversation.getId());
        gone.failing = true;

        registry.tick(Instant.now());

        assertThat(gone.completed).isTrue();
        assertThat(registry.openConnections()).isZero();
    }

    // --- closing

    @Test
    void anUnregisteredConnectionReceivesNothingMore() {
        RecordingSink sink = new RecordingSink();
        UUID id = registry.register(ORG, grant("ann", Set.of(conversation.getId()), FAR, List.of(), Set.of()), sink);

        registry.unregister(id);
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 1)));

        assertThat(sink.events()).containsExactly("stream.ready");
        assertThat(registry.openConnections()).isZero();
    }

    @Test
    void shuttingDownEndsEveryConnection() {
        RecordingSink ann = connect(ORG, "ann", conversation.getId());
        RecordingSink bob = connect(ORG, "bob", conversation.getId());

        registry.stop();

        assertThat(ann.completed).isTrue();
        assertThat(bob.completed).isTrue();
        assertThat(registry.openConnections()).isZero();
    }

    @Test
    void theHeartbeatSchedulerStartsAndStopsWithTheBean() {
        ChatStreamRegistry started = new ChatStreamRegistry(new ConversationMapper(), new ChatMetrics((MeterRegistry) null), 1);

        started.start();
        started.stop();

        assertThat(started.openConnections()).isZero();
    }
}
