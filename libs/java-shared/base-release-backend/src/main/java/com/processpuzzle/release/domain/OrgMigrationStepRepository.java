package com.processpuzzle.release.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgMigrationStepRepository extends JpaRepository<OrgMigrationStep, UUID> {

    /** The steps the next release runs, in the order it runs them. */
    List<OrgMigrationStep> findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(String orgKey);

    /** The steps releases ran, newest release first, each release's in the order it ran them. */
    List<OrgMigrationStep> findByOrgKeyAndReleaseNumberIsNotNullOrderByReleaseNumberDescSequenceNoAsc(String orgKey);

    @Modifying(flushAutomatically = true)
    @Query("delete from OrgMigrationStep s where s.orgKey = :orgKey and s.releaseNumber is null")
    int deletePending(@Param("orgKey") String orgKey);

    /** One bulk statement, as {@code OrganizationDeletedEvent} recommends. */
    @Modifying
    @Query("delete from OrgMigrationStep s where s.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
