package com.processpuzzle.state.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Every state machine of a tenant in one statement, for erasing the tenant. Not
     * {@link #deleteByOrgKey}, which loads every row and removes it one by one. States and
     * transitions are columns of the row, so there is no child table to clear first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from StateMachineDefinition d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
