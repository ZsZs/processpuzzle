package com.processpuzzle.state.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Every finder is scoped by {@code orgKey}, same discipline as
 * {@link StateMachineDefinitionRepository}: the inherited {@code findById}/{@code existsById} take
 * a {@link DiagramDefinitionKey}, so an unscoped read of another tenant's layout is not
 * expressible by accident.
 */
public interface DiagramDefinitionRepository
        extends JpaRepository<DiagramDefinition, DiagramDefinitionKey>,
        JpaSpecificationExecutor<DiagramDefinition> {

    Optional<DiagramDefinition> findByOrgKeyAndEntityName(String orgKey, String entityName);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    List<DiagramDefinition> findByOrgKey(String orgKey);

    void deleteByOrgKey(String orgKey);
}
