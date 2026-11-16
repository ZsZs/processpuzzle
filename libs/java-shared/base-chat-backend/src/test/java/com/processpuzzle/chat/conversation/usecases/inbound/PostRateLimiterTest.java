package com.processpuzzle.chat.conversation.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.chat.conversation.usecases.exception.ChatRateLimitedException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PostRateLimiterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong nanos = new AtomicLong(5 * SECOND);

    private PostRateLimiter limiter(int burst, double perSecond) {
        return new PostRateLimiter(burst, perSecond, nanos::get);
    }

    @Test
    void allowsABurstAndThenRefusesWithTheTimeToWait() {
        PostRateLimiter limiter = limiter(3, 1);
        for (int i = 0; i < 3; i++) {
            limiter.acquire("org", "ann");
        }

        assertThatThrownBy(() -> limiter.acquire("org", "ann"))
                .isInstanceOfSatisfying(ChatRateLimitedException.class, e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void refillsOverTime() {
        PostRateLimiter limiter = limiter(2, 2);
        limiter.acquire("org", "ann");
        limiter.acquire("org", "ann");
        assertThatThrownBy(() -> limiter.acquire("org", "ann")).isInstanceOf(ChatRateLimitedException.class);

        nanos.addAndGet(SECOND / 2);

        assertThatCode(() -> limiter.acquire("org", "ann")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire("org", "ann")).isInstanceOf(ChatRateLimitedException.class);
    }

    @Test
    void neverRefillsBeyondTheBurst() {
        PostRateLimiter limiter = limiter(2, 1);
        nanos.addAndGet(3600 * SECOND);
        limiter.acquire("org", "ann");
        limiter.acquire("org", "ann");

        assertThatThrownBy(() -> limiter.acquire("org", "ann")).isInstanceOf(ChatRateLimitedException.class);
    }

    @Test
    void countsEachUserAndEachOrganizationOnItsOwn() {
        PostRateLimiter limiter = limiter(1, 1);
        limiter.acquire("org", "ann");

        assertThatCode(() -> limiter.acquire("org", "bob")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquire("other", "ann")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.acquire("org", "ann")).isInstanceOf(ChatRateLimitedException.class);
    }

    @Test
    void aBurstOfZeroSwitchesTheLimitOff() {
        PostRateLimiter limiter = limiter(0, 1);

        assertThatCode(() -> {
            for (int i = 0; i < 1000; i++) {
                limiter.acquire("org", "ann");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void forgetsUsersWhoseBucketHasRefilledOnceThereAreManyOfThem() {
        PostRateLimiter limiter = limiter(1, 1);
        for (int i = 0; i < 10_001; i++) {
            limiter.acquire("org", "user-" + i);
        }
        nanos.addAndGet(10 * SECOND);

        // The next acquire sweeps; a swept user starts with a full bucket, which is what a refilled one holds.
        limiter.acquire("org", "someone-else");

        assertThatCode(() -> limiter.acquire("org", "user-0")).doesNotThrowAnyException();
    }
}
