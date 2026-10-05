package com.processpuzzle.starter.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefinitionProvenanceRepository extends JpaRepository<DefinitionProvenance, UUID> {

    Optional<DefinitionProvenance> findByOrgKeyAndKindAndKey(String orgKey, String kind, String key);

    List<DefinitionProvenance> findByOrgKeyAndStarterId(String orgKey, String starterId);

    void deleteByOrgKey(String orgKey);
}
