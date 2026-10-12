package com.processpuzzle.release.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgReleaseItemRepository extends JpaRepository<OrgReleaseItem, UUID> {

    List<OrgReleaseItem> findByOrgKeyOrderByReleaseNumberDescSequenceNoAsc(String orgKey);

    /** Every item up to a release, oldest first, so that a later item of a definition replaces an earlier one. */
    List<OrgReleaseItem> findByOrgKeyAndReleaseNumberLessThanEqualOrderByReleaseNumberAscSequenceNoAsc(
            String orgKey, long releaseNumber);

    /** One bulk statement, as {@code OrganizationDeletedEvent} recommends. */
    @Modifying
    @Query("delete from OrgReleaseItem i where i.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
