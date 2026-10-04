package com.processpuzzle.ai.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentPhotoRepository extends JpaRepository<EnrollmentPhoto, UUID> {

    List<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(String orgKey, String entityName, UUID objectId);

    /** The galleries of a candidate list, for a recognition. */
    List<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdInAndStatus(
            String orgKey, String entityName, Collection<UUID> objectIds, EnrollmentPhotoStatus status);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    List<EnrollmentPhoto> findByVisionJobId(UUID visionJobId);
}
