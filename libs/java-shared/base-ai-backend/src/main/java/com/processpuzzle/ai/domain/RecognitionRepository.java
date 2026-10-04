package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionRepository extends JpaRepository<Recognition, UUID> {

    Optional<Recognition> findByOrgKeyAndRecognitionId(String orgKey, UUID recognitionId);

    Optional<Recognition> findByVisionJobId(UUID visionJobId);

    List<Recognition> findByCreatedAtBefore(Instant horizon);
}
