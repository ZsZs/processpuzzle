package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TaskDefinitionRepository
        extends JpaRepository<TaskDefinition, TaskDefinitionKey>, JpaSpecificationExecutor<TaskDefinition> {

    Optional<TaskDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<TaskDefinition> findByOrgKey(String orgKey);

    List<TaskDefinition> findByOrgKeyAndIdIn(String orgKey, List<String> ids);
}
