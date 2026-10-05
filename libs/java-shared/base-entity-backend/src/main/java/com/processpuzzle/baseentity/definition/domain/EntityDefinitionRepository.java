package com.processpuzzle.baseentity.definition.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EntityDefinitionRepository
    extends JpaRepository<BaseEntityDefinition, UUID>, JpaSpecificationExecutor<BaseEntityDefinition> {

    Optional<BaseEntityDefinition> findByOrgKeyAndCode(String orgKey, String code);

    boolean existsByOrgKeyAndCode(String orgKey, String code);

    List<BaseEntityDefinition> findAllByOrgKey(String orgKey);
}
