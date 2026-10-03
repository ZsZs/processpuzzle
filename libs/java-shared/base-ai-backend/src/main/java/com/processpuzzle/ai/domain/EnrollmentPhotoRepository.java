package com.processpuzzle.ai.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentPhotoRepository extends JpaRepository<EnrollmentPhoto, UUID> {

    List<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(String orgKey, String entityName, UUID objectId);

    Optional<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdAndPhotoId(
            String orgKey, String entityName, UUID objectId, UUID photoId);

    boolean existsByOrgKeyAndMediaKey(String orgKey, UUID mediaKey);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    List<EnrollmentPhoto> findByVisionJobId(UUID visionJobId);
}
