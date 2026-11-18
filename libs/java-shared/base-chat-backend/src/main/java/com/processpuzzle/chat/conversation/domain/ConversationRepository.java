package com.processpuzzle.chat.conversation.domain;

import com.processpuzzle.chat.definition.domain.SubjectType;
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

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndOrgKey(UUID id, String orgKey);

    /**
     * Locks the row until the transaction ends, provided the conversation has the given status — a no-op
     * update, which takes the row lock in every database. Posting takes it before looking for a replay and
     * before {@link #advanceSeq}, which is what serializes the posts of one conversation.
     *
     * <p>A bulk statement rather than a locking read of the entity: the entity has been loaded already, for
     * the access decision, and a locking query would hand back that instance with its stale
     * {@code lastSeq}.
     *
     * @return 1 when the row was locked, 0 when the conversation does not exist or has another status
     */
    @Modifying(flushAutomatically = true)
    @Query("update Conversation c set c.status = c.status where c.id = :id and c.orgKey = :orgKey and c.status = :status")
    int lockIfStatus(@Param("id") UUID id, @Param("orgKey") String orgKey, @Param("status") ConversationStatus status);

    /** Takes the next message number. Only after {@link #lockIfStatus}; read it back with {@link #lastSeqOf}. */
    @Modifying(flushAutomatically = true)
    @Query("update Conversation c set c.lastSeq = c.lastSeq + 1 where c.id = :id and c.orgKey = :orgKey")
    int advanceSeq(@Param("id") UUID id, @Param("orgKey") String orgKey);

    /** The number from the database, not from a loaded entity. */
    @Query("select c.lastSeq from Conversation c where c.id = :id")
    long lastSeqOf(@Param("id") UUID id);

    List<Conversation> findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(
            String orgKey, SubjectType subjectType, String subjectId);

    boolean existsByOrgKeyAndBindingKey(String orgKey, String bindingKey);

    /**
     * The conversations of one definition that nothing has happened in since {@code cutoff}: the newest message, or
     * the opening of a conversation that has none, is older. Oldest activity first, so a backlog is worked off in order.
     */
    @Query("select c.id from Conversation c where c.orgKey = :orgKey and c.definitionKey = :definitionKey "
            + "and coalesce((select max(m.createdAt) from Message m where m.conversationId = c.id), c.createdAt) < :cutoff "
            + "order by c.createdAt asc")
    List<UUID> findIdleIds(@Param("orgKey") String orgKey, @Param("definitionKey") String definitionKey,
                           @Param("cutoff") Instant cutoff, Pageable page);

    @Modifying(flushAutomatically = true)
    @Query("delete from Conversation c where c.orgKey = :orgKey and c.id in :ids")
    int deleteByIds(@Param("orgKey") String orgKey, @Param("ids") Collection<UUID> ids);

    /** Takes the creator off the conversations a user opened — erasure keeps the conversation, not who started it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Conversation c set c.createdBy = :erased, c.createdByName = null where c.orgKey = :orgKey and c.createdBy = :userSub")
    int anonymizeCreator(@Param("orgKey") String orgKey, @Param("userSub") String userSub, @Param("erased") String erased);

    /** Every conversation of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Conversation c where c.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
