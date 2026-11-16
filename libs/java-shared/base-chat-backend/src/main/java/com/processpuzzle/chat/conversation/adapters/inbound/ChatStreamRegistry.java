package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.common.ChatMetrics;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.events.MessagePostedEvent;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import com.processpuzzle.chat.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The open live connections of this instance, and who gets what.
 *
 * <p>A connection carries the followed conversations its user could read when it opened (see
 * {@code OpenChatStream}); this class decides nothing about access, it only matches: a posted message goes to
 * every connection of its organization whose conversation set contains the conversation, and a marker change
 * goes to the user's own connections — adding or dropping the conversation, and telling the user's other tabs.
 * Both arrive as events <em>after</em> the transaction commits, so nobody sees a message that rolled back.
 *
 * <p>Delivery is best effort and in-process. A connection that fails to take an event is dropped; the client
 * reconnects and replays from the {@code seq} it holds. A message that is also in the replay may arrive twice,
 * and clients ignore a {@code seq} they have. A second backend instance would not see this instance's events —
 * relaying them (Postgres {@code LISTEN/NOTIFY}) is hardening work, behind the same two event types.
 *
 * <p>A heartbeat comment about every {@code heartbeat-seconds} keeps proxies from closing an idle connection
 * (nginx does after 60 s) and finds dead ones; the same tick ends connections whose grant has expired.
 */
@Component
public class ChatStreamRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ChatStreamRegistry.class);

    /** Data of {@code stream.ready}. */
    public record Ready(String expiresAt, long heartbeatSeconds) {
    }

    /** Data of {@code message.posted}. */
    public record MessagePosted(UUID conversationId, ChatMessage message) {
    }

    /** Data of {@code read.updated}. */
    public record ReadUpdated(UUID conversationId, long lastReadSeq, boolean following) {
    }

    /** Data of {@code resync}. */
    public record Resync(UUID conversationId) {
    }

    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final ConversationMapper mapper;
    private final long heartbeatSeconds;
    private ScheduledExecutorService scheduler;

    public ChatStreamRegistry(ConversationMapper mapper, ChatMetrics metrics,
                              @Value("${base-chat.stream.heartbeat-seconds:25}") long heartbeatSeconds) {
        this.mapper = mapper;
        this.heartbeatSeconds = Math.max(1, heartbeatSeconds);
        metrics.streamConnections(this::openConnections);
    }

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "chat-stream-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> tick(Instant.now()), heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        connections.values().forEach(Connection::complete);
        connections.clear();
    }

    /**
     * Opens a connection: sends {@code stream.ready}, then what the client missed, then lets live events
     * through. The connection is registered first and the initial burst sent under its lock, so a live event
     * waits for the burst instead of overtaking it — and none is lost in between.
     *
     * @return the connection's id, for {@link #unregister}
     */
    public UUID register(String orgKey, StreamGrant grant, StreamSink sink) {
        Connection connection = new Connection(orgKey, grant.userSub(), grant.conversations(), grant.expiresAt(), sink);
        synchronized (connection) {
            connections.put(connection.id, connection);
            connection.push(null, "stream.ready", new Ready(grant.expiresAt().toString(), heartbeatSeconds));
            grant.replay().forEach(message -> connection.push(eventId(message), "message.posted", posted(message)));
            grant.resync().forEach(conversationId -> connection.push(null, "resync", new Resync(conversationId)));
        }
        dropIfDead(connection);
        return connection.id;
    }

    public void unregister(UUID connectionId) {
        connections.remove(connectionId);
    }

    /** How many connections this instance holds. */
    public int openConnections() {
        return connections.size();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessagePosted(MessagePostedEvent event) {
        MessagePosted data = posted(event.message());
        String id = eventId(event.message());
        for (Connection connection : connections.values()) {
            if (connection.orgKey.equals(event.orgKey()) && connection.conversations.contains(event.conversationId())) {
                connection.push(id, "message.posted", data);
                dropIfDead(connection);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReadMarkerChanged(ReadMarkerChangedEvent event) {
        ReadUpdated data = new ReadUpdated(event.conversationId(), event.lastReadSeq(), event.following());
        for (Connection connection : connections.values()) {
            if (connection.orgKey.equals(event.orgKey()) && connection.userSub.equals(event.userSub())) {
                if (event.following()) {
                    connection.conversations.add(event.conversationId());
                } else {
                    connection.conversations.remove(event.conversationId());
                }
                connection.push(null, "read.updated", data);
                dropIfDead(connection);
            }
        }
    }

    /** One heartbeat: ends the connections whose grant ran out and pings the rest. Package-private for tests. */
    void tick(Instant now) {
        for (Connection connection : connections.values()) {
            if (!now.isBefore(connection.expiresAt)) {
                connections.remove(connection.id);
                connection.complete();
            } else {
                connection.ping();
                dropIfDead(connection);
            }
        }
    }

    private void dropIfDead(Connection connection) {
        if (connection.dead) {
            connections.remove(connection.id);
            connection.complete();
        }
    }

    private MessagePosted posted(Message message) {
        return new MessagePosted(message.getConversationId(), mapper.toModel(message));
    }

    private static String eventId(Message message) {
        return message.getConversationId() + ":" + message.getSeq();
    }

    /** One client connection. Sends are serialized: a sink is not safe for two threads at once. */
    private static final class Connection {

        private final UUID id = UUID.randomUUID();
        private final String orgKey;
        private final String userSub;
        private final Set<UUID> conversations = ConcurrentHashMap.newKeySet();
        private final Instant expiresAt;
        private final StreamSink sink;
        private volatile boolean dead;

        private Connection(String orgKey, String userSub, Set<UUID> conversations, Instant expiresAt, StreamSink sink) {
            this.orgKey = orgKey;
            this.userSub = userSub;
            this.conversations.addAll(conversations);
            this.expiresAt = expiresAt;
            this.sink = sink;
        }

        private synchronized void push(String eventId, String event, Object data) {
            if (!dead) {
                try {
                    sink.send(eventId, event, data);
                } catch (IOException | RuntimeException e) {
                    markDead(e);
                }
            }
        }

        private synchronized void ping() {
            if (!dead) {
                try {
                    sink.comment("keepalive");
                } catch (IOException | RuntimeException e) {
                    markDead(e);
                }
            }
        }

        private synchronized void complete() {
            dead = true;
            try {
                sink.complete();
            } catch (RuntimeException e) {
                LOG.debug("Completing a chat stream failed", e);
            }
        }

        private void markDead(Exception cause) {
            dead = true;
            LOG.debug("A chat stream is gone", cause);
        }
    }
}
