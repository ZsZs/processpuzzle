package com.processpuzzle.release.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgPendingRecordsRepository extends JpaRepository<OrgPendingRecords, UUID> {

    /** The files the next release loads, in the order it loads them. */
    List<OrgPendingRecords> findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(String orgKey);

    boolean existsByOrgKeyAndReleaseNumberIsNull(String orgKey);

    /** One bulk statement, as {@code OrganizationDeletedEvent} recommends. */
    @Modifying
    @Query("delete from OrgPendingRecords r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
