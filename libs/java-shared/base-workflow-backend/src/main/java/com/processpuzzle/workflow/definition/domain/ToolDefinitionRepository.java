package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ToolDefinitionRepository
        extends JpaRepository<ToolDefinition, ToolDefinitionKey>, JpaSpecificationExecutor<ToolDefinition> {

    Optional<ToolDefinition> findByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    boolean existsByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    List<ToolDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    /**
     * Every tool definition, its embedded auth config included of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ToolDefinition t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
