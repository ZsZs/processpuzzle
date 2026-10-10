package com.processpuzzle.ai.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentPhotoRepository extends JpaRepository<EnrollmentPhoto, UUID> {

    List<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(String orgKey, String entityName, UUID objectId);

    /** The galleries of a candidate list, for a recognition. */
    List<EnrollmentPhoto> findByOrgKeyAndEntityNameAndObjectIdInAndStatus(
            String orgKey, String entityName, Collection<UUID> objectIds, EnrollmentPhotoStatus status);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    List<EnrollmentPhoto> findByVisionJobId(UUID visionJobId);

    /**
     * Every gallery photo of a tenant in one statement, for erasing the tenant. Its crops are objects in
     * the media store, which the eraser removes by prefix once this has committed.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from EnrollmentPhoto p where p.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
