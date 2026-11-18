package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two tabs of one user follow the same conversation at the same moment. Both must succeed — the one that loses
 * the race for the marker's key must not lose its own transaction with it — and the marker ends where the
 * furthest of them put it.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-marker-concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.chat.conversation.domain")
@EnableJpaRepositories("com.processpuzzle.chat.conversation.domain")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReadMarkersConcurrencyTest {

    private static final int TABS = 8;

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        ReadMarkers readMarkers(ReadMarkerRepository markers, ApplicationEventPublisher events) {
            return new ReadMarkers(markers, events);
        }

        @Bean
        RecordedEvents recordedEvents() {
            return new RecordedEvents();
        }
    }

    /** Collects what the markers announce, as a stream registry would hear it. */
    static class RecordedEvents {
        final List<ReadMarkerChangedEvent> events = new CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        void on(ReadMarkerChangedEvent event) {
            events.add(event);
        }
    }

    @Autowired
    private ReadMarkers markers;
    @Autowired
    private ReadMarkerRepository repository;
    @Autowired
    private RecordedEvents recorded;

    @Test
    void tabsFollowingAtOnceAllSucceedAndTheMarkerEndsAtTheFurthest() throws Exception {
        UUID id = UUID.randomUUID();
        List<Callable<Long>> tabs = new ArrayList<>();
        for (int tab = 1; tab <= TABS; tab++) {
            long seq = tab * 10L;
            tabs.add(() -> markers.advanceTo(ORG, id, "ann", seq));
        }

        List<Long> standings = runTogether(tabs);

        assertThat(repository.lastReadOf(id, "ann")).contains(TABS * 10L);
        assertThat(standings).hasSize(TABS).allSatisfy(standing -> assertThat(standing).isBetween(10L, TABS * 10L));
        assertThat(repository.findByConversationIdAndUserSub(id, "ann")).isPresent();
        assertThat(recorded.events).filteredOn(event -> event.conversationId().equals(id)).hasSize(TABS)
                .allSatisfy(event -> assertThat(event.following()).isTrue());
    }

    @Test
    void differentUsersFollowingTheSameConversationDoNotInterfere() throws Exception {
        UUID id = UUID.randomUUID();
        List<Callable<Long>> users = new ArrayList<>();
        for (int user = 0; user < TABS; user++) {
            String sub = "user-" + user;
            users.add(() -> markers.advanceTo(ORG, id, sub, 3));
        }

        runTogether(users);

        for (int user = 0; user < TABS; user++) {
            assertThat(repository.lastReadOf(id, "user-" + user)).contains(3L);
        }
    }

    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
