package com.processpuzzle.baseentity.unit.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The units organizations added, always read and written within one organization. */
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    Optional<OrganizationUnit> findByOrgKeyAndCode(String orgKey, String code);

    boolean existsByOrgKeyAndCode(String orgKey, String code);

    List<OrganizationUnit> findAllByOrgKeyOrderByCode(String orgKey);

    Page<OrganizationUnit> findAllByOrgKey(String orgKey, Pageable pageable);

    Page<OrganizationUnit> findAllByOrgKeyAndStatus(String orgKey, UnitStatus status, Pageable pageable);

    /** Every unit of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from OrganizationUnit u where u.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
