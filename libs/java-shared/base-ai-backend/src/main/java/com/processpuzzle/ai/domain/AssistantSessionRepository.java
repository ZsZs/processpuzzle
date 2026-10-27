package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantSessionRepository extends JpaRepository<AssistantSession, UUID> {

    /** The session, only when it belongs to this designer in this organization. */
    Optional<AssistantSession> findByIdAndOrgKeyAndOwnerSubject(UUID id, String orgKey, String ownerSubject);

    /** A bulk delete does not cascade, so the messages of the organization's sessions go first. */
    @Modifying
    @Query("delete from AssistantSessionMessage m where m.session.id in "
        + "(select s.id from AssistantSession s where s.orgKey = :orgKey)")
    void deleteMessagesOfOrganization(@Param("orgKey") String orgKey);

    @Modifying
    @Query("delete from AssistantSession s where s.orgKey = :orgKey")
    int deleteByOrganization(@Param("orgKey") String orgKey);

    @Modifying
    @Query("delete from AssistantSessionMessage m where m.session.id in "
        + "(select s.id from AssistantSession s where s.lastUsedAt < :cutoff)")
    void deleteMessagesIdleBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from AssistantSession s where s.lastUsedAt < :cutoff")
    int deleteIdleBefore(@Param("cutoff") Instant cutoff);
}
