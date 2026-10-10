package com.processpuzzle.ai.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecognitionProfileRepository extends JpaRepository<RecognitionProfile, UUID> {

    Optional<RecognitionProfile> findByOrgKeyAndEntityName(String orgKey, String entityName);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    Page<RecognitionProfile> findByOrgKey(String orgKey, Pageable pageable);

    /** Every recognition profile of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RecognitionProfile p where p.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
