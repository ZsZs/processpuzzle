package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryAssistantQuotaTest {

    private static final Instant DAY_ONE = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void anOrganizationIsRefusedOnceItHasUsedItsTurnsForTheDay() {
        InMemoryAssistantQuota quota = new InMemoryAssistantQuota(fixed(DAY_ONE), 2, 1_000_000);

        quota.checkTurnAllowed("acme");
        quota.recordTurn("acme", 10);
        quota.checkTurnAllowed("acme");
        quota.recordTurn("acme", 10);

        assertThatThrownBy(() -> quota.checkTurnAllowed("acme")).isInstanceOf(AssistantQuotaExceededException.class);
    }

    @Test
    void anOrganizationIsRefusedOnceItHasUsedItsTokensForTheDay() {
        InMemoryAssistantQuota quota = new InMemoryAssistantQuota(fixed(DAY_ONE), 100, 50);

        quota.recordTurn("acme", 50);

        assertThatThrownBy(() -> quota.checkTurnAllowed("acme")).isInstanceOf(AssistantQuotaExceededException.class);
    }

    @Test
    void theLimitsAreKeptPerOrganization() {
        InMemoryAssistantQuota quota = new InMemoryAssistantQuota(fixed(DAY_ONE), 1, 1_000_000);

        quota.recordTurn("acme", 10);

        assertThatThrownBy(() -> quota.checkTurnAllowed("acme")).isInstanceOf(AssistantQuotaExceededException.class);
        quota.checkTurnAllowed("globex");
    }

    @Test
    void theCountsStartAgainTheNextUtcDay() {
        MutableClock clock = new MutableClock(DAY_ONE);
        InMemoryAssistantQuota quota = new InMemoryAssistantQuota(clock, 1, 1_000_000);
        quota.recordTurn("acme", 10);
        assertThatThrownBy(() -> quota.checkTurnAllowed("acme")).isInstanceOf(AssistantQuotaExceededException.class);

        clock.now = Instant.parse("2026-10-28T00:00:01Z");

        quota.checkTurnAllowed("acme");
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** A clock whose instant the test moves, to cross a day boundary. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
