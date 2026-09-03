package com.processpuzzle.platformadmin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * {@link JpaSpecificationExecutor} was already here before the aggregate moved, so
 * {@code FindAllOrganizations} needs no new query method: the RSQL {@code where} parameter compiles
 * to a {@code Specification} and pages through the inherited
 * {@code findAll(Specification, Pageable)}.
 */
public interface OrganizationRepository
        extends JpaRepository<Organization, String>, JpaSpecificationExecutor<Organization> {

    /**
     * Every issuer the resource server must trust, read at startup and on refresh.
     *
     * <p>A projection rather than {@code findAll()}: realm-per-tenant means the security
     * configuration needs one row per organization and nothing but its key, and loading whole
     * entities to read one column of each is the kind of thing that stops being free at a thousand
     * tenants.
     */
    @Query("select o.key from Organization o")
    List<String> findAllKeys();
}
