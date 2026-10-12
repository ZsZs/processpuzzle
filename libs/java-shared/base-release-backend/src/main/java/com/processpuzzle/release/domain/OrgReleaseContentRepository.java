package com.processpuzzle.release.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgReleaseContentRepository extends JpaRepository<OrgReleaseContent, UUID> {

    /** One bulk statement, as {@code OrganizationDeletedEvent} recommends. */
    @Modifying
    @Query("delete from OrgReleaseContent c where c.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
