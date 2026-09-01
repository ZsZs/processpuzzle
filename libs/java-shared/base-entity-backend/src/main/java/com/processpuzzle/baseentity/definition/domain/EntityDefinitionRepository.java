package com.processpuzzle.baseentity.definition.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityDefinitionRepository
    extends JpaRepository<BaseEntityDefinition, UUID>, JpaSpecificationExecutor<BaseEntityDefinition> {

    Optional<BaseEntityDefinition> findByCode(String code);

    boolean existsByCode(String code);

    /** Definitions that still declare {@code parentCode} anywhere in componentParents — used by the delete guard. */
    @Query(
        value = "select * from base_entity_definition d where d.component_parents @> jsonb_build_array(cast(:parentCode as text))",
        nativeQuery = true)
    List<BaseEntityDefinition> findByComponentParentsContaining(@Param("parentCode") String parentCode);
}
