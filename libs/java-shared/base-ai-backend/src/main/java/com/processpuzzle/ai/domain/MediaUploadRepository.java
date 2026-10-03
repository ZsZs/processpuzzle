package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    Optional<MediaUpload> findByOrgKeyAndMediaKey(String orgKey, UUID mediaKey);

    List<MediaUpload> findByUsedAtIsNullAndExpiresAtBefore(Instant horizon);
}
