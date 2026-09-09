package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ToolDefinitionRepository
        extends JpaRepository<ToolDefinition, ToolDefinitionKey>, JpaSpecificationExecutor<ToolDefinition> {

    Optional<ToolDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);
}
