package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ArtifactDefinitionRepository
        extends JpaRepository<ArtifactDefinition, ArtifactDefinitionKey>, JpaSpecificationExecutor<ArtifactDefinition> {

    Optional<ArtifactDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<ArtifactDefinition> findByOrgKey(String orgKey);

    List<ArtifactDefinition> findByOrgKeyAndIdIn(String orgKey, List<String> ids);
}
