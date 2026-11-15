package com.processpuzzle.chat.conversation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, MessageKey> {

    /** The stored message of a post, for a replay with the same client id. */
    Optional<Message> findByConversationIdAndAuthorSubAndClientMessageId(UUID conversationId, String authorSub, UUID clientMessageId);

    /** One page of history, highest {@code seq} first, below the cursor {@code before} (exclusive). */
    List<Message> findByConversationIdAndSeqLessThanOrderBySeqDesc(UUID conversationId, long before, Pageable page);

    /** What a stream that reconnects missed: the messages after the last {@code seq} the client has, oldest first. */
    List<Message> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(UUID conversationId, long after, Pageable page);

    /** Every message of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Message m where m.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
