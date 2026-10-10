package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    Optional<MediaUpload> findByOrgKeyAndMediaKey(String orgKey, UUID mediaKey);

    List<MediaUpload> findByUsedAtIsNullAndExpiresAtBefore(Instant horizon);

    /**
     * Every upload slot of a tenant in one statement, for erasing the tenant. The uploaded objects go
     * with the rest of the tenant's media, by prefix, after commit.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from MediaUpload u where u.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
