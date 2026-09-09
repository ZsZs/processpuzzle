package com.processpuzzle.widget.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Internal to the module — not part of {@code widget :: domain}, which exposes only
 * {@link WidgetDefinitionStatus}. Consumers go through the use cases.
 */
public interface WidgetDefinitionRepository
        extends JpaRepository<WidgetDefinition, WidgetDefinitionKey>, JpaSpecificationExecutor<WidgetDefinition> {

    Optional<WidgetDefinition> findByOrgKeyAndKey(String orgKey, String key);

    /**
     * Used before {@code save} on create. {@code JpaRepository.save} calls {@code merge} rather than
     * {@code persist} for an assigned (non-generated) id, which silently turns a duplicate create
     * into an update — so the 409 has to come from an explicit existence check, exactly as
     * base-app's AppDefinitionRepository does.
     */
    boolean existsByOrgKeyAndKey(String orgKey, String key);

    void deleteByOrgKey(String orgKey);
}
