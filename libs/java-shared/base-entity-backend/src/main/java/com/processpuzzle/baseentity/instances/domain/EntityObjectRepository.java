package com.processpuzzle.baseentity.instances.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityObjectRepository
    extends JpaRepository<EntityObject, UUID>, JpaSpecificationExecutor<EntityObject> {

    long countByOrgKey(String orgKey);

    boolean existsByOrgKeyAndEntityDefinitionCode(String orgKey, String entityDefinitionCode);

    List<EntityObject> findAllByOrgKeyAndEntityDefinitionCode(String orgKey, String entityDefinitionCode);

    /** By id within one organization; callers go through {@link EntityObjectScope}, which picks the organization. */
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

    /**
     * The payloads alone of a tenant's objects of the given definitions, without loading the objects
     * as entities — for reading the files they refer to just before the rows are deleted in bulk.
     */
    @Query("select o.entityDefinitionCode as entityDefinitionCode, o.payload as payload from EntityObject o "
        + "where o.orgKey = :orgKey and o.entityDefinitionCode in :entityDefinitionCodes")
    List<PayloadView> findPayloadsByOrgKeyAndEntityDefinitionCodeIn(
        @Param("orgKey") String orgKey, @Param("entityDefinitionCodes") Collection<String> entityDefinitionCodes);

    /** Every object of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from EntityObject o where o.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);

    /** An object's payload with the code of its definition. */
    interface PayloadView {

        String getEntityDefinitionCode();

        Map<String, Object> getPayload();
    }
}
