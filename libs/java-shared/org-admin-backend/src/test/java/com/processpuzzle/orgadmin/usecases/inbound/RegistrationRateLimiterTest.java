package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.inbound.exception.RegistrationRateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RegistrationProperties properties = new RegistrationProperties();
    private RegistrationRateLimiter limiter;

    private RegistrationRateLimiter limiter() {
        properties.setMaxAttempts(2);
        properties.setWindow(Duration.ofMinutes(10));
        return new RegistrationRateLimiter(properties, clock);
    }

    @Test
    void refusesAttemptsBeyondTheLimitAndSaysWhenToRetry() {
        limiter = limiter();
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");
        clock.advance(Duration.ofMinutes(4));

        assertThatThrownBy(() -> limiter.acquire("1.1.1.1"))
                .isInstanceOfSatisfying(RegistrationRateLimitedException.class,
                        ex -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMinutes(6)));
    }

    @Test
    void countsEachAddressSeparately() {
        limiter = limiter();
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");

        limiter.acquire("2.2.2.2");
    }

    @Test
    void startsAFreshWindowOnceTheOldOneExpires() {
        limiter = limiter();
        limiter.acquire("1.1.1.1");
        limiter.acquire("1.1.1.1");
        clock.advance(Duration.ofMinutes(10));

        limiter.acquire("1.1.1.1");
    }
}
