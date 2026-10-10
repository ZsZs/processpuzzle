package com.processpuzzle.widget.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Every widget definition of a tenant in one statement, for erasing the tenant. Not
     * {@link #deleteByOrgKey}, which loads every row and removes it one by one. The props schema and
     * the ports are columns of the row, so there is no child table to clear first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from WidgetDefinition w where w.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
