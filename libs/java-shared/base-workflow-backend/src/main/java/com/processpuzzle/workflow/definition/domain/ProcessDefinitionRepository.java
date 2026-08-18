package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProcessDefinitionRepository
        extends JpaRepository<ProcessDefinition, ProcessDefinitionKey>, JpaSpecificationExecutor<ProcessDefinition> {

    @EntityGraph(attributePaths = {"roles", "workProducts", "tasks"})
    Optional<ProcessDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<ProcessDefinition> findByOrgKeyAndExtendsProcessId(String orgKey, String extendsProcessId);

    List<ProcessDefinition> findByOrgKey(String orgKey);
}
