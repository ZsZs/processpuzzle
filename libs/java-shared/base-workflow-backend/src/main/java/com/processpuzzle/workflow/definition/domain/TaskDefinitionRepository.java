package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository
        extends JpaRepository<TaskDefinition, TaskDefinitionKey>, JpaSpecificationExecutor<TaskDefinition> {

    Optional<TaskDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<TaskDefinition> findByOrgKey(String orgKey);

    List<TaskDefinition> findByOrgKeyAndIdIn(String orgKey, List<String> ids);

    /**
     * Every task definition of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from TaskDefinition t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
