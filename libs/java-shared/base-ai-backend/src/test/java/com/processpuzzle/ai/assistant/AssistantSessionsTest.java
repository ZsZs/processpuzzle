package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.ai.domain.AssistantSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** The conversations: opened, read back, kept per designer, and expired when idle. Runs against H2. */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.ai.domain")
@EnableJpaRepositories("com.processpuzzle.ai.domain")
class AssistantSessionsTest {

    private static final String ORG = "acme";
    private static final String ADA = "ada-subject";
    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private AssistantSessionRepository repository;

    @Test
    void aNewSessionHasNoHistoryYet() {
        AssistantSessions sessions = sessions(new MutableClock(T0), 20);
        UUID session = sessions.open(ORG, ADA);

        assertThat(sessions.history(session, ORG, ADA)).isEmpty();
    }

    @Test
    void theHistoryReadsBackTheExchangesInOrder() {
        AssistantSessions sessions = sessions(new MutableClock(T0), 20);
        UUID session = sessions.open(ORG, ADA);

        sessions.append(session, ORG, ADA, "Define an invoice.", "Saved the draft.");
        sessions.append(session, ORG, ADA, "Rename it.", "Renamed.");

        assertThat(sessions.history(session, ORG, ADA)).containsExactly(
            new UserMessage("Define an invoice."), new AssistantMessage("Saved the draft."),
            new UserMessage("Rename it."), new AssistantMessage("Renamed."));
    }

    @Test
    void onlyTheMostRecentMessagesAreReadBack() {
        AssistantSessions sessions = sessions(new MutableClock(T0), 2);
        UUID session = sessions.open(ORG, ADA);
        sessions.append(session, ORG, ADA, "first", "answer one");
        sessions.append(session, ORG, ADA, "second", "answer two");

        assertThat(sessions.history(session, ORG, ADA)).containsExactly(
            new UserMessage("second"), new AssistantMessage("answer two"));
    }

    @Test
    void aSessionIsOnlyVisibleToItsDesignerInItsOrganization() {
        AssistantSessions sessions = sessions(new MutableClock(T0), 20);
        UUID session = sessions.open(ORG, ADA);

        assertThatThrownBy(() -> sessions.history(session, ORG, "grace-subject"))
            .isInstanceOf(AssistantSessionNotFoundException.class);
        assertThatThrownBy(() -> sessions.history(session, "globex", ADA))
            .isInstanceOf(AssistantSessionNotFoundException.class);
        assertThatThrownBy(() -> sessions.append(session, ORG, "grace-subject", "hi", "hello"))
            .isInstanceOf(AssistantSessionNotFoundException.class);
    }

    @Test
    void anIdleSessionExpiresAndAnActiveOneDoesNot() {
        MutableClock clock = new MutableClock(T0);
        AssistantSessions sessions = sessions(clock, 20);
        UUID idle = sessions.open(ORG, ADA);
        sessions.append(idle, ORG, ADA, "old", "old answer");
        clock.now = T0.plus(Duration.ofHours(20));
        UUID active = sessions.open(ORG, ADA);
        clock.now = T0.plus(Duration.ofHours(25));

        int expired = sessions.purgeIdle();

        assertThat(expired).isEqualTo(1);
        assertThat(repository.existsById(idle)).isFalse();
        assertThat(repository.existsById(active)).isTrue();
    }

    private AssistantSessions sessions(Clock clock, int historyMessages) {
        return new AssistantSessions(repository, clock, historyMessages, Duration.ofHours(24));
    }

    /** A clock whose instant the test moves, to age a session. */
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
