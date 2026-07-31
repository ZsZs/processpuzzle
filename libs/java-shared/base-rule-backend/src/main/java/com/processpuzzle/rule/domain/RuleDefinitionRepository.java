package com.processpuzzle.rule.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code orgKey}. The inherited {@code findById}/{@code existsById}
 * take a {@link RuleDefinitionKey}, so an unscoped read of another tenant's row is not
 * expressible by accident; use cases call the explicit {@code ...ByOrgKey...} methods.
 */
public interface RuleDefinitionRepository
        extends JpaRepository<RuleDefinition, RuleDefinitionKey>, JpaSpecificationExecutor<RuleDefinition> {

    Optional<RuleDefinition> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<RuleDefinition> findByOrgKey(String orgKey);

    List<RuleDefinition> findByOrgKeyAndContext(String orgKey, String context);

    /** The rules of {@code orgKey} that extend {@code extendsRuleId} — its dependents. */
    List<RuleDefinition> findByOrgKeyAndExtendsRuleId(String orgKey, String extendsRuleId);

    void deleteByOrgKey(String orgKey);
}
