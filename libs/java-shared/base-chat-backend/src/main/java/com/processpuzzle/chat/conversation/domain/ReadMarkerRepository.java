package com.processpuzzle.chat.conversation.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * Creates the marker unless the user already has one, in one atomic statement — no exception to catch, no
     * second transaction. When another transaction is inserting the same marker this waits for it to finish and
     * then does nothing, so a refusal always means the row is there to be locked.
     *
     * <p>Native, because JPA has no insert-if-absent; {@code on conflict do nothing} is PostgreSQL's, and H2 in
     * its PostgreSQL mode, which the tests run in, understands it too.
     *
     * @return 1 when the marker was created, 0 when there already was one
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into chat_read_marker (conversation_id, user_sub, org_key, last_read_seq, updated_at) "
            + "values (:conversationId, :userSub, :orgKey, :seq, :now) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub,
                       @Param("orgKey") String orgKey, @Param("seq") long seq, @Param("now") Instant now);

    /**
     * The marker, locked for writing until the transaction ends, so that what is read of it is what it is when this
     * transaction writes. Moving a marker takes it before comparing: read-then-write under READ COMMITTED lets two
     * writers both compare with the old number, and the lower one land last. With the lock the second writer waits
     * here and compares with what the first one wrote.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ReadMarker m where m.conversationId = :conversationId and m.userSub = :userSub")
    Optional<ReadMarker> lockByConversationIdAndUserSub(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub);

    /**
     * Moves the marker forward to {@code seq}; never back, and not at all when it is already there. The guard is
     * only exact after {@link #lockByConversationIdAndUserSub}, which is what keeps another writer from changing
     * the row between the comparison and the write.
     *
     * @return 1 when it moved
     */
    @Modifying(flushAutomatically = true)
    @Query("update ReadMarker m set m.lastReadSeq = :seq, m.updatedAt = :now "
            + "where m.conversationId = :conversationId and m.userSub = :userSub and m.lastReadSeq < :seq")
    int advance(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub,
                @Param("seq") long seq, @Param("now") Instant now);

    /** The number from the database, not from a loaded entity. */
    @Query("select m.lastReadSeq from ReadMarker m where m.conversationId = :conversationId and m.userSub = :userSub")
    Optional<Long> lastReadOf(@Param("conversationId") UUID conversationId, @Param("userSub") String userSub);

    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey and m.conversationId in :conversationIds")
    int deleteByConversationIds(@Param("orgKey") String orgKey, @Param("conversationIds") java.util.Collection<UUID> conversationIds);

    /** Every marker of one user, for erasing them. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey and m.userSub = :userSub")
    int deleteByUser(@Param("orgKey") String orgKey, @Param("userSub") String userSub);

    /** @return 1 when the user followed the conversation */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey and m.conversationId = :conversationId and m.userSub = :userSub")
    int unfollow(@Param("orgKey") String orgKey, @Param("conversationId") UUID conversationId, @Param("userSub") String userSub);

    /** Every marker of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ReadMarker m where m.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
