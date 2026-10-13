package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleDefinitionRepository
        extends JpaRepository<RoleDefinition, RoleDefinitionKey>, JpaSpecificationExecutor<RoleDefinition> {

    Optional<RoleDefinition> findByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    boolean existsByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    List<RoleDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    List<RoleDefinition> findByOrgKeyAndIdInAndChannel(String orgKey, List<String> ids, Channel channel);

    /**
     * Every role id of the organization, draft or released, once each — read by tenant erasure before
     * {@link #eraseByOrgKey} so that each role can be removed from the identity provider once the erasure has committed.
     */
    @Query("select distinct r.id from RoleDefinition r where r.orgKey = :orgKey")
    List<String> findIdsByOrgKey(@Param("orgKey") String orgKey);

    /**
     * Every role definition of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RoleDefinition r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
