package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.usecases.exception.ChatRateLimitedException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A token bucket per user and organization for posting: a burst of {@code base-chat.rate-limit.burst} messages, then
 * {@code base-chat.rate-limit.per-second} more every second. In memory, so each backend instance counts on its own —
 * that bounds a flood from one account, it is not a distributed quota. A burst of 0 switches the limit off.
 *
 * <p>Every post that reaches the limiter takes a token, a replay of an earlier post included: a client that retries
 * in a loop is exactly what the limit is for.
 */
@Component
public class PostRateLimiter {

    private static final int EVICT_ABOVE = 10_000;
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private record Bucket(double tokens, long updatedNanos) {
    }

    private final int burst;
    private final double perSecond;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public PostRateLimiter(@Value("${base-chat.rate-limit.burst:20}") int burst,
                           @Value("${base-chat.rate-limit.per-second:2}") double perSecond) {
        this(burst, perSecond, System::nanoTime);
    }

    PostRateLimiter(int burst, double perSecond, LongSupplier nanoClock) {
        this.burst = Math.max(0, burst);
        this.perSecond = perSecond > 0 ? perSecond : 1;
        this.nanoClock = nanoClock;
    }

    /** @throws ChatRateLimitedException when {@code user} has used up the burst and the refill */
    public void acquire(String orgKey, String user) {
        if (burst == 0) {
            return;
        }
        long now = nanoClock.getAsLong();
        evictFull(now);
        double[] wait = new double[1];
        buckets.compute(orgKey + '\u0000' + user, (key, current) -> {
            double tokens = current == null ? burst : Math.min(burst, current.tokens() + elapsedSeconds(current, now) * perSecond);
            if (tokens >= 1) {
                return new Bucket(tokens - 1, now);
            }
            wait[0] = (1 - tokens) / perSecond;
            return new Bucket(tokens, now);
        });
        if (wait[0] > 0) {
            throw new ChatRateLimitedException(Duration.ofMillis((long) Math.ceil(wait[0] * 1000)));
        }
    }

    private static double elapsedSeconds(Bucket bucket, long now) {
        return Math.max(0, now - bucket.updatedNanos()) / NANOS_PER_SECOND;
    }

    /** A bucket that has refilled completely holds nothing a fresh one would not, so it can go. */
    private void evictFull(long now) {
        if (buckets.size() > EVICT_ABOVE) {
            buckets.values().removeIf(bucket -> bucket.tokens() + elapsedSeconds(bucket, now) * perSecond >= burst);
        }
    }
}
