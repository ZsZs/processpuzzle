package com.processpuzzle.artifact.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code orgKey}, exactly as {@code RuleDefinitionRepository}
 * documents: the inherited {@code findById}/{@code existsById} take an {@link ArtifactKey}, so
 * an unscoped read of another tenant's row is not expressible by accident.
 */
public interface ArtifactRepository
        extends JpaRepository<Artifact, ArtifactKey>, JpaSpecificationExecutor<Artifact> {

    Optional<Artifact> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<Artifact> findByOrgKey(String orgKey);

    void deleteByOrgKey(String orgKey);
}
