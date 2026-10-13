package com.processpuzzle.state.domain;

import com.processpuzzle.core.definition.Channel;
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

    Optional<StateMachineDefinition> findByOrgKeyAndEntityNameAndChannel(String orgKey, String entityName, Channel channel);

    boolean existsByOrgKeyAndEntityNameAndChannel(String orgKey, String entityName, Channel channel);

    List<StateMachineDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    /** Both rows of every one of a tenant's definitions — for removing them all, never for reading one. */
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
