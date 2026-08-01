package com.processpuzzle.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AppDefinitionRepository
        extends JpaRepository<AppDefinition, AppDefinitionKey>, JpaSpecificationExecutor<AppDefinition> {

    Optional<AppDefinition> findByOrgKeyAndId(String orgKey, String id);

    /**
     * Used before {@code save} on create. {@code JpaRepository.save} calls {@code merge} rather
     * than {@code persist} for an assigned (non-generated) id, which silently turns a duplicate
     * create into an update — so the 409 has to come from an explicit existence check, exactly as
     * {@code CreateRule} does.
     */
    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<AppDefinition> findByOrgKey(String orgKey);

    void deleteByOrgKey(String orgKey);
}
