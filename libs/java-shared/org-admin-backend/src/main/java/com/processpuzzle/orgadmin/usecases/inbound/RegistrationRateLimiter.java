package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationRateLimitedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window attempt counter per client address. In-memory, so each backend instance counts on its
 * own; that bounds abuse of an unauthenticated endpoint, it is not a distributed quota. Every
 * attempt counts, successful or not, because the point is to slow account creation.
 */
@Component
public class RegistrationRateLimiter {

    private record Window(Instant start, int count) {
    }

    private final RegistrationProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RegistrationRateLimiter(RegistrationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RegistrationRateLimiter(RegistrationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws RegistrationRateLimitedException when {@code clientKey} has used up its attempts */
    public void acquire(String clientKey) {
        Instant now = clock.instant();
        Duration span = properties.getWindow();
        evictExpired(now, span);
        Window updated = windows.compute(clientKey, (key, current) ->
                current == null || !now.isBefore(current.start().plus(span))
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (updated.count() > properties.getMaxAttempts()) {
            Duration remaining = Duration.between(now, updated.start().plus(span));
            throw new RegistrationRateLimitedException(remaining.isNegative() ? Duration.ZERO : remaining);
        }
    }

    private void evictExpired(Instant now, Duration span) {
        if (windows.size() > 10_000) {
            windows.values().removeIf(window -> !now.isBefore(window.start().plus(span)));
        }
    }
}
