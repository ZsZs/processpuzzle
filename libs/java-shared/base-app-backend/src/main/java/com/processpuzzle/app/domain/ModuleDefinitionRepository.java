package com.processpuzzle.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleDefinitionRepository extends JpaRepository<ModuleDefinition, ModuleDefinitionKey> {

    Optional<ModuleDefinition> findByOrgKeyAndKey(String orgKey, String key);

    /**
     * Used before {@code save} on create: {@code save} merges rather than persists for an assigned
     * id, which would turn a duplicate create into a silent update. Same reason as
     * {@code AppDefinitionRepository.existsByOrgKeyAndId}.
     */
    boolean existsByOrgKeyAndKey(String orgKey, String key);

    List<ModuleDefinition> findByOrgKey(String orgKey);

    /** Called by {@code DeleteOrganization}: modules are scoped by the organization like everything else. */
    void deleteByOrgKey(String orgKey);
}
