package com.processpuzzle.app.domain;

import com.processpuzzle.core.definition.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleDefinitionRepository extends JpaRepository<ModuleDefinition, ModuleDefinitionKey> {

    Optional<ModuleDefinition> findByOrgKeyAndKeyAndChannel(String orgKey, String key, Channel channel);

    /**
     * Used before {@code save} on create: {@code save} merges rather than persists for an assigned
     * id, which would turn a duplicate create into a silent update. Same reason as
     * {@code AppDefinitionRepository.existsByOrgKeyAndId}.
     */
    boolean existsByOrgKeyAndKeyAndChannel(String orgKey, String key, Channel channel);

    List<ModuleDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    List<ModuleDefinition> findByOrgKeyAndChannelAndKeyIn(String orgKey, Channel channel, List<String> keys);

    /**
     * Called by {@code TenantDataCleaner} when the tenant is deleted: modules are scoped by the
     * organization like everything else. It used to be called by {@code DeleteOrganization}
     * directly, which was possible while base-app owned that use case; the cascade now arrives as
     * {@code OrganizationDeletedEvent}.
     */
    void deleteByOrgKey(String orgKey);
}
