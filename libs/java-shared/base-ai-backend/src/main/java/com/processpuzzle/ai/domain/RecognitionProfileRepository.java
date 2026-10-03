package com.processpuzzle.ai.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionProfileRepository extends JpaRepository<RecognitionProfile, UUID> {

    Optional<RecognitionProfile> findByOrgKeyAndEntityName(String orgKey, String entityName);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    Page<RecognitionProfile> findByOrgKey(String orgKey, Pageable pageable);
}
