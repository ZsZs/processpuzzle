package com.processpuzzle.baseentity.instances.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityObjectRepository
    extends JpaRepository<EntityObject, UUID>, JpaSpecificationExecutor<EntityObject> {

    boolean existsByOrgKeyAndEntityDefinitionCode(String orgKey, String entityDefinitionCode);

    List<EntityObject> findAllByEntityDefinitionCode(String entityDefinitionCode);

    /** By id within one organization: an id of another organization's object is not found. */
    Optional<EntityObject> findByIdAndOrgKey(UUID id, String orgKey);

    /**
     * Blunt "does any payload anywhere contain this id" containment check, used for the
     * delete-guard on /entities/{id} (cascade=false path). Unindexed — acceptable at current
     * volumes, worth tightening to a scoped FOREIGN_KEY-attribute lookup once that becomes a
     * hot path (see README).
     */
    @Query(
        value = "select exists (select 1 from entity_object o where o.payload::text like concat('%', :referencedId, '%'))",
        nativeQuery = true)
    boolean existsAnyReferenceTo(@Param("referencedId") String referencedId);
}
