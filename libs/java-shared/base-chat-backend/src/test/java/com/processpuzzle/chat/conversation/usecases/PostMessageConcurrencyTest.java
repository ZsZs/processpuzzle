package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.usecases.inbound.PostMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkerInsert;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbering promise, with real transactions on several threads: the {@code seq} of a conversation is
 * gap-free whatever the interleaving, and a post replayed at the same moment as its original takes no number.
 * Each call goes through the {@code PostMessage} bean, so the transaction boundary is the real one.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-post-concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.chat.conversation.domain")
@EnableJpaRepositories("com.processpuzzle.chat.conversation.domain")
// Real commits are the point: the test itself must not wrap the threads' transactions in one that rolls back.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostMessageConcurrencyTest {

    private static final int THREADS = 8;
    private static final int POSTS_PER_THREAD = 15;

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        ReadMarkerInsert readMarkerInsert(ReadMarkerRepository markers) {
            return new ReadMarkerInsert(markers);
        }

        @Bean
        ReadMarkers readMarkers(ReadMarkerRepository markers, ReadMarkerInsert inserter, ApplicationEventPublisher events) {
            return new ReadMarkers(markers, inserter, events);
        }

        @Bean
        PostMessage postMessage(ConversationRepository conversations, MessageRepository messages, ReadMarkers markers,
                                ApplicationEventPublisher events) {
            ResolveAccess access = mock(ResolveAccess.class);
            when(access.of(org.mockito.ArgumentMatchers.any(Conversation.class))).thenReturn(new ConversationAccess(true, true, true, true));
            CurrentPrincipal principal = mock(CurrentPrincipal.class);
            when(principal.subject()).thenReturn(Optional.of("sub-author"));
            when(principal.displayName()).thenReturn(Optional.of("Ann Author"));
            return new PostMessage(conversations, messages, access, mock(OrganizationGuard.class), principal, markers, events, 4000);
        }
    }

    @Autowired
    private PostMessage post;
    @Autowired
    private ConversationRepository conversations;
    @Autowired
    private MessageRepository messages;
    @Autowired
    private ReadMarkerRepository markers;

    private UUID newConversation() {
        Conversation conversation = conversation();
        conversation.setId(null);
        conversation.setSubjectId(UUID.randomUUID().toString());
        conversation.setBindingKey(UUID.randomUUID().toString());
        return conversations.saveAndFlush(conversation).getId();
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

    @Test
    void concurrentPostsGetEveryNumberOnceWithoutGaps() throws Exception {
        UUID id = newConversation();
        List<Callable<List<Long>>> workers = new ArrayList<>();
        for (int worker = 0; worker < THREADS; worker++) {
            workers.add(() -> {
                List<Long> numbers = new ArrayList<>();
                for (int i = 0; i < POSTS_PER_THREAD; i++) {
                    numbers.add(post.post(ORG, id, UUID.randomUUID(), "message").message().getSeq());
                }
                return numbers;
            });
        }

        List<Long> all = runTogether(workers).stream().flatMap(List::stream).toList();

        int total = THREADS * POSTS_PER_THREAD;
        assertThat(all).hasSize(total);
        assertThat(new HashSet<>(all)).containsExactlyInAnyOrderElementsOf(allNumbersUpTo(total));
        assertThat(conversations.lastSeqOf(id)).isEqualTo(total);
        assertThat(markers.lastReadOf(id, "sub-author")).as("the poster followed it and kept up with their own posts").contains((long) total);
        assertThat(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(id, Long.MAX_VALUE, PageRequest.of(0, total + 1)))
                .extracting(Message::getSeq).isSortedAccordingTo(java.util.Comparator.reverseOrder()).hasSize(total);
    }

    @Test
    void theSamePostSentAtTheSameMomentIsStoredOnceAndTheOtherGetsTheStoredMessage() throws Exception {
        UUID id = newConversation();
        UUID clientId = UUID.randomUUID();
        List<Callable<PostedMessage>> twins = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            twins.add(() -> post.post(ORG, id, clientId, "once"));
        }

        List<PostedMessage> results = runTogether(twins);

        assertThat(results).filteredOn(PostedMessage::created).hasSize(1);
        assertThat(results).extracting(posted -> posted.message().getSeq()).containsOnly(1L);
        assertThat(conversations.lastSeqOf(id)).isEqualTo(1);
        assertThat(markers.lastReadOf(id, "sub-author")).contains(1L);
    }

    private static Set<Long> allNumbersUpTo(int total) {
        Set<Long> numbers = new HashSet<>();
        for (long n = 1; n <= total; n++) {
            numbers.add(n);
        }
        return numbers;
    }
}
