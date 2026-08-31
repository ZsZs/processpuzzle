package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RoleDefinitionRepository
        extends JpaRepository<RoleDefinition, RoleDefinitionKey>, JpaSpecificationExecutor<RoleDefinition> {

    Optional<RoleDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<RoleDefinition> findByOrgKey(String orgKey);

    List<RoleDefinition> findByOrgKeyAndIdIn(String orgKey, List<String> ids);
}
