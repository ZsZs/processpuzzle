package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository
        extends JpaRepository<TaskDefinition, TaskDefinitionKey>, JpaSpecificationExecutor<TaskDefinition> {

    Optional<TaskDefinition> findByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    boolean existsByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    List<TaskDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    List<TaskDefinition> findByOrgKeyAndIdInAndChannel(String orgKey, List<String> ids, Channel channel);

    /**
     * Every task definition of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from TaskDefinition t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
