package com.processpuzzle.rule.domain;

import com.processpuzzle.core.definition.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code orgKey}. The inherited {@code findById}/{@code existsById}
 * take a {@link RuleDefinitionKey}, so an unscoped read of another tenant's row is not
 * expressible by accident; use cases call the explicit {@code ...ByOrgKey...} methods.
 */
public interface RuleDefinitionRepository
        extends JpaRepository<RuleDefinition, RuleDefinitionKey>, JpaSpecificationExecutor<RuleDefinition> {

    Optional<RuleDefinition> findByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    boolean existsByOrgKeyAndIdAndChannel(String orgKey, String id, Channel channel);

    List<RuleDefinition> findByOrgKeyAndChannel(String orgKey, Channel channel);

    /** Both rows of every one of a tenant's rules — for removing them all, never for reading one. */
    List<RuleDefinition> findByOrgKey(String orgKey);

    List<RuleDefinition> findByOrgKeyAndContextAndChannel(String orgKey, String context, Channel channel);

    /** The rules of {@code orgKey} that extend {@code extendsRuleId} in one channel — its dependents there. */
    List<RuleDefinition> findByOrgKeyAndExtendsRuleIdAndChannel(String orgKey, String extendsRuleId, Channel channel);

    void deleteByOrgKey(String orgKey);

    /** The ids alone, for unregistering a tenant's rules from the engine without loading them. */
    @Query("select r.id from RuleDefinition r where r.orgKey = :orgKey")
    List<String> findIdsByOrgKey(@Param("orgKey") String orgKey);

    /**
     * The {@code fields} collection table, which has to go before the rows it belongs to: bulk
     * deletes do not cascade, and a JPQL statement cannot address a collection table. Native, for
     * that reason only.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "delete from rule_definition_fields where org_key = :orgKey", nativeQuery = true)
    int eraseFieldsByOrgKey(@Param("orgKey") String orgKey);

    /**
     * Every rule of a tenant in one statement, for erasing the tenant. Not {@link #deleteByOrgKey},
     * which loads every row and removes it one by one — fine for an import, slow for a large tenant.
     * Run {@link #eraseFieldsByOrgKey} first.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RuleDefinition r where r.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
