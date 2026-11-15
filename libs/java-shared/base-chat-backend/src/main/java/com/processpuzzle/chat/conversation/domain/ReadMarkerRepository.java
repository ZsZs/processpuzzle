package com.processpuzzle.chat.conversation.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The markers are written by statements, never by saving a loaded entity: {@code last_read_seq} is not
 * updatable, so two tabs of one user cannot overwrite each other's progress with a stale copy.
 */
public interface ReadMarkerRepository extends JpaRepository<ReadMarker, ReadMarkerKey> {

    Optional<ReadMarker> findByConversationIdAndUserSub(UUID conversationId, String userSub);

    /** What the user follows in an organization, most recently touched first. */
    List<ReadMarker> findByOrgKeyAndUserSubOrderByUpdatedAtDesc(String orgKey, String userSub, Pageable page);

    /** The user's markers among some conversations, for showing a list with unread counts. */
    List<ReadMarker> findByUserSubAndConversationIdIn(String userSub, Collection<UUID> conversationIds);

    /**
     * Moves the marker forward to {@code seq}; never back, and not at all when it is already there.
     *
     * <p>The maximum is computed in the statement, from the row as it is when the statement writes, rather than
     * guarded by a {@code where last_read_seq < :seq}: when two statements wait on one row, PostgreSQL re-checks
     * the condition after the first commits but not every database does, and a stale pass would write a lower
     * number over a higher one.
     *
     * @return 1 when the user follows the conversation — whether or not the marker moved
     */
    @Modifying(flushAutomatically = true)
    @Query("update ReadMarker m set "
            + "m.updatedAt = case when m.lastReadSeq < :seq then :now else m.updatedAt end, "
            + "m.lastReadSeq = case when m.lastReadSeq < :seq then :seq else m.lastReadSeq end "
            + "where m.conversationId = :conversationId and m.userSub = :userSub")
    int advance(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub,
                @Param("seq") long seq, @Param("now") Instant now);

    /** The number from the database, not from a loaded entity. */
    @Query("select m.lastReadSeq from ReadMarker m where m.conversationId = :conversationId and m.userSub = :userSub")
    Optional<Long> lastReadOf(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub);

    /** @return 1 when the user followed the conversation */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey and m.conversationId = :conversationId and m.userSub = :userSub")
    int unfollow(@Param("orgKey") String orgKey, @Param("conversationId") UUID conversationId, @Param("userSub") String userSub);

    /** Every marker of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
