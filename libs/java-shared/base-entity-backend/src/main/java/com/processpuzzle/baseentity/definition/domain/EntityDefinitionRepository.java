package com.processpuzzle.baseentity.definition.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EntityDefinitionRepository
    extends JpaRepository<BaseEntityDefinition, UUID>, JpaSpecificationExecutor<BaseEntityDefinition> {

    Optional<BaseEntityDefinition> findByCode(String code);

    boolean existsByCode(String code);
}
