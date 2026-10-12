package com.processpuzzle.release.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgReleaseRepository extends JpaRepository<OrgRelease, UUID> {

    List<OrgRelease> findByOrgKeyOrderByReleaseNumberDesc(String orgKey);

    Optional<OrgRelease> findFirstByOrgKeyOrderByReleaseNumberDesc(String orgKey);

    /** One bulk statement, as {@code OrganizationDeletedEvent} recommends. */
    @Modifying
    @Query("delete from OrgRelease r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
