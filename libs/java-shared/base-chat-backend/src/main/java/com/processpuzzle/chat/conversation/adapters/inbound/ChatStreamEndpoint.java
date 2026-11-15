package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenChatStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The live stream of {@code base-chat-api.yaml}, written by hand because a generated interface cannot return an
 * {@link SseEmitter}. Everything that can be refused is refused before the emitter exists, so a refusal is an
 * ordinary JSON error.
 *
 * <p>{@code X-Accel-Buffering: no} tells nginx — which buffers responses by default — to pass each event on as
 * it is written; without it the events arrive in batches, or not until the connection ends.
 */
@RestController
public class ChatStreamEndpoint {

    static final int MAX_AFTER_ENTRIES = 500;

    /** Beyond the grant's expiry, so the registry ends the connection before the container times it out. */
    private static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private final OpenChatStream openStream;
    private final ChatStreamRegistry registry;

    public ChatStreamEndpoint(OpenChatStream openStream, ChatStreamRegistry registry) {
        this.openStream = openStream;
        this.registry = registry;
    }

    @GetMapping("/organizations/{orgKey}/chat/stream")
    public ResponseEntity<SseEmitter> stream(@PathVariable String orgKey, @RequestParam(required = false) String after) {
        StreamGrant grant = openStream.open(orgKey, parseAfter(after));

        long timeoutMillis = Math.max(1, Duration.between(Instant.now(), grant.expiresAt()).plus(TIMEOUT_MARGIN).toMillis());
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        UUID connection = registry.register(orgKey, grant, new SseSink(emitter));
        emitter.onCompletion(() -> registry.unregister(connection));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> registry.unregister(connection));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache().noTransform())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    /** {@code <uuid>:<seq>,<uuid>:<seq>}: for each conversation the client holds, the newest {@code seq} it has. */
    static Map<UUID, Long> parseAfter(String after) {
        Map<UUID, Long> parsed = new LinkedHashMap<>();
        if (after == null || after.isBlank()) {
            return parsed;
        }
        String[] entries = after.split(",");
        if (entries.length > MAX_AFTER_ENTRIES) {
            throw new InvalidChatRequestException("'after' lists at most %d conversations".formatted(MAX_AFTER_ENTRIES));
        }
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            try {
                long seq = Long.parseLong(entry.substring(colon + 1).trim());
                if (colon < 0 || seq < 0) {
                    throw new IllegalArgumentException("not a pair");
                }
                parsed.merge(UUID.fromString(entry.substring(0, colon).trim()), seq, Math::max);
            } catch (IllegalArgumentException e) {
                throw new InvalidChatRequestException("'after' must be <conversationId>:<seq> pairs, comma separated");
            }
        }
        return parsed;
    }
}
