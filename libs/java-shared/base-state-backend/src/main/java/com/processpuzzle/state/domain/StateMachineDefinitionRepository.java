package com.processpuzzle.state.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Every finder is scoped by {@code orgKey}, same discipline as {@code RuleDefinitionRepository}:
 * the inherited {@code findById}/{@code existsById} take a {@link StateMachineDefinitionKey}, so
 * an unscoped read of another tenant's row is not expressible by accident.
 */
public interface StateMachineDefinitionRepository
        extends JpaRepository<StateMachineDefinition, StateMachineDefinitionKey>,
        JpaSpecificationExecutor<StateMachineDefinition> {

    Optional<StateMachineDefinition> findByOrgKeyAndEntityName(String orgKey, String entityName);

    boolean existsByOrgKeyAndEntityName(String orgKey, String entityName);

    List<StateMachineDefinition> findByOrgKey(String orgKey);

    void deleteByOrgKey(String orgKey);
}
