package com.processpuzzle.chat.conversation.domain;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The marker mapping binds, only moves forward, and is written by statements that a stale copy cannot undo. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-marker-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.chat.conversation.domain")
@EnableJpaRepositories("com.processpuzzle.chat.conversation.domain")
class ReadMarkerPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ReadMarkerRepository markers;
    @Autowired
    private EntityManager entityManager;

    private ReadMarker marker(UUID conversationId, String user, long lastRead) {
        return ReadMarker.builder().conversationId(conversationId).userSub(user).orgKey(ORG).lastReadSeq(lastRead)
                .updatedAt(Instant.parse("2026-11-15T10:00:00Z")).build();
    }

    private void refresh() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void roundTripsAMarkerAndFindsItByConversationAndUser() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 4));
        refresh();

        ReadMarker found = markers.findByConversationIdAndUserSub(id, "ann").orElseThrow();

        assertThat(found.getOrgKey()).isEqualTo(ORG);
        assertThat(found.getLastReadSeq()).isEqualTo(4);
        assertThat(found.getUpdatedAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(markers.findByConversationIdAndUserSub(id, "bob")).isEmpty();
        assertThat(markers.lastReadOf(id, "ann")).contains(4L);
        assertThat(markers.lastReadOf(id, "bob")).isEmpty();
    }

    @Test
    void aSecondInsertOfTheSameMarkerViolatesTheKey() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 1));
        refresh();
        ReadMarker duplicate = marker(id, "ann", 2);

        entityManager.persist(duplicate);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void createsAMarkerOnceAndLeavesAnExistingOneAlone() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-16T10:00:00Z");

        assertThat(markers.insertIfAbsent(id, "ann", ORG, 4, now)).isEqualTo(1);
        assertThat(markers.insertIfAbsent(id, "ann", ORG, 9, now)).as("there is one already").isZero();
        assertThat(markers.insertIfAbsent(id, "bob", ORG, 2, now)).as("another user's is another marker").isEqualTo(1);
        refresh();

        ReadMarker created = markers.findByConversationIdAndUserSub(id, "ann").orElseThrow();
        assertThat(created.getLastReadSeq()).as("the first number stands").isEqualTo(4);
        assertThat(created.getOrgKey()).isEqualTo(ORG);
        assertThat(created.getUpdatedAt()).isEqualTo(now);
        assertThat(markers.lastReadOf(id, "bob")).contains(2L);
    }

    @Test
    void locksTheMarkerOfOneUserInOneConversationAndNoOtherOne() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 4));
        markers.saveAndFlush(marker(id, "bob", 9));
        refresh();

        assertThat(markers.lockByConversationIdAndUserSub(id, "ann")).get().extracting(ReadMarker::getLastReadSeq).isEqualTo(4L);
        assertThat(markers.lockByConversationIdAndUserSub(id, "nobody")).isEmpty();
        assertThat(markers.lockByConversationIdAndUserSub(UUID.randomUUID(), "ann")).isEmpty();
    }

    @Test
    void advancingOnlyMovesForward() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 5));

        Instant later = Instant.parse("2026-11-16T10:00:00Z");

        assertThat(markers.advance(id, "ann", 3, later)).as("behind it").isZero();
        assertThat(markers.advance(id, "ann", 5, later)).as("where it is").isZero();
        refresh();
        ReadMarker unmoved = markers.findByConversationIdAndUserSub(id, "ann").orElseThrow();
        assertThat(unmoved.getLastReadSeq()).as("a lower or equal number changes nothing").isEqualTo(5);
        assertThat(unmoved.getUpdatedAt()).as("and does not count as a touch").isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));

        assertThat(markers.advance(id, "ann", 9, later)).isEqualTo(1);
        refresh();
        ReadMarker moved = markers.findByConversationIdAndUserSub(id, "ann").orElseThrow();
        assertThat(moved.getLastReadSeq()).isEqualTo(9);
        assertThat(moved.getUpdatedAt()).isEqualTo(later);

        assertThat(markers.advance(id, "nobody", 9, later)).as("somebody who does not follow it").isZero();
    }

    @Test
    void aStaleCopyOfAMarkerCannotWriteAnOldNumberBack() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 1));
        refresh();
        ReadMarker stale = markers.findByConversationIdAndUserSub(id, "ann").orElseThrow();

        markers.advance(id, "ann", 7, Instant.now());
        stale.setOrgKey("org-changed-in-memory");
        markers.saveAndFlush(stale);
        refresh();

        assertThat(markers.lastReadOf(id, "ann")).contains(7L);
        assertThat(markers.findByConversationIdAndUserSub(id, "ann").orElseThrow().getOrgKey()).isEqualTo(ORG);
    }

    @Test
    void whatAUserFollowsComesBackMostRecentlyTouchedFirstAndScopedToTheOrganization() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        markers.saveAndFlush(marker(older, "ann", 0));
        ReadMarker recent = marker(newer, "ann", 0);
        recent.setUpdatedAt(Instant.parse("2026-11-15T12:00:00Z"));
        markers.saveAndFlush(recent);
        ReadMarker otherOrg = marker(elsewhere, "ann", 0);
        otherOrg.setOrgKey("org-2");
        markers.saveAndFlush(otherOrg);
        markers.saveAndFlush(marker(UUID.randomUUID(), "bob", 0));

        assertThat(markers.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(ORG, "ann", PageRequest.of(0, 10)))
                .extracting(ReadMarker::getConversationId).containsExactly(newer, older);
        assertThat(markers.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(ORG, "ann", PageRequest.of(0, 1))).hasSize(1);
        assertThat(markers.findByUserSubAndConversationIdIn("ann", List.of(older, elsewhere)))
                .extracting(ReadMarker::getConversationId).containsExactlyInAnyOrder(older, elsewhere);
    }

    @Test
    void unfollowingDeletesOnlyTheUsersOwnMarkerInThatOrganization() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 2));
        markers.saveAndFlush(marker(id, "bob", 2));

        assertThat(markers.unfollow("org-2", id, "ann")).isZero();
        assertThat(markers.unfollow(ORG, id, "ann")).isEqualTo(1);
        assertThat(markers.unfollow(ORG, id, "ann")).isZero();

        assertThat(markers.lastReadOf(id, "ann")).isEmpty();
        assertThat(markers.lastReadOf(id, "bob")).contains(2L);
    }

    @Test
    void erasesOneTenantsMarkers() {
        UUID id = UUID.randomUUID();
        markers.saveAndFlush(marker(id, "ann", 1));
        ReadMarker theirs = marker(id, "dan", 1);
        theirs.setOrgKey("org-2");
        markers.saveAndFlush(theirs);

        assertThat(markers.eraseByOrgKey(ORG)).isEqualTo(1);

        assertThat(markers.lastReadOf(id, "ann")).isEmpty();
        assertThat(markers.lastReadOf(id, "dan")).contains(1L);
    }
}
