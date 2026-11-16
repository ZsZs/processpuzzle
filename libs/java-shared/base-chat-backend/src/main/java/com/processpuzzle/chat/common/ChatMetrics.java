package com.processpuzzle.chat.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * What the chat module reports to Micrometer — nothing when the host has no {@link MeterRegistry}, so the module
 * runs the same without actuator.
 *
 * <ul>
 *   <li>{@code chat.messages.posted} — messages stored (a replay is not counted).
 *   <li>{@code chat.messages.rejected{reason}} — posts refused: {@code rate_limited}, {@code not_writable} or
 *       {@code forbidden}.
 *   <li>{@code chat.stream.connections} — live streams this instance holds.
 * </ul>
 *
 * No tag carries an organization or a user: the cardinality would grow with the tenants.
 */
@Component
public class ChatMetrics {

    private final MeterRegistry registry;

    @Autowired
    public ChatMetrics(ObjectProvider<MeterRegistry> registry) {
        this(registry.getIfAvailable());
    }

    /** @param registry where to report; null reports nothing */
    public ChatMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void messagePosted() {
        if (registry != null) {
            Counter.builder("chat.messages.posted").description("Chat messages stored").register(registry).increment();
        }
    }

    /** @param reason {@code rate_limited}, {@code not_writable} or {@code forbidden} */
    public void messageRejected(String reason) {
        if (registry != null) {
            Counter.builder("chat.messages.rejected").tag("reason", reason).description("Chat posts refused").register(registry).increment();
        }
    }

    public void streamConnections(Supplier<Number> open) {
        if (registry != null) {
            Gauge.builder("chat.stream.connections", open).description("Live chat streams held by this instance").register(registry);
        }
    }
}
