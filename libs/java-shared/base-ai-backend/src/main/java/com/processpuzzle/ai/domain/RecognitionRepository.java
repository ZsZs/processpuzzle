package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecognitionRepository extends JpaRepository<Recognition, UUID> {

    Optional<Recognition> findByOrgKeyAndRecognitionId(String orgKey, UUID recognitionId);

    Optional<Recognition> findByVisionJobId(UUID visionJobId);

    List<Recognition> findByCreatedAtBefore(Instant horizon);

    /**
     * The four collection tables of a tenant's recognitions — frames, candidates, candidates without a
     * gallery, ranking — which have to go before the rows they belong to: bulk deletes do not cascade,
     * and a JPQL statement cannot address a collection table. They carry no {@code org_key}, so each is
     * reached through its owner. Native, for that reason only.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from ai_recognition_frames where recognition_id in "
            + "(select recognition_id from ai_recognitions where org_key = :orgKey)", nativeQuery = true)
    int eraseFramesByOrgKey(@Param("orgKey") String orgKey);

    /** See {@link #eraseFramesByOrgKey}. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from ai_recognition_candidate_ids where recognition_id in "
            + "(select recognition_id from ai_recognitions where org_key = :orgKey)", nativeQuery = true)
    int eraseCandidateIdsByOrgKey(@Param("orgKey") String orgKey);

    /** See {@link #eraseFramesByOrgKey}. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from ai_recognition_without_gallery where recognition_id in "
            + "(select recognition_id from ai_recognitions where org_key = :orgKey)", nativeQuery = true)
    int eraseCandidatesWithoutGalleryByOrgKey(@Param("orgKey") String orgKey);

    /** See {@link #eraseFramesByOrgKey}. */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from ai_recognition_ranking where recognition_id in "
            + "(select recognition_id from ai_recognitions where org_key = :orgKey)", nativeQuery = true)
    int eraseRankingByOrgKey(@Param("orgKey") String orgKey);

    /**
     * Every recognition of a tenant in one statement, for erasing the tenant. Run the four collection
     * erasures first. Its crops are in the media store and go by prefix after commit.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Recognition r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
