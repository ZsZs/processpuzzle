package com.processpuzzle.rule.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RuleDefinitionRepository
        extends JpaRepository<RuleDefinition, String>, JpaSpecificationExecutor<RuleDefinition> {

    List<RuleDefinition> findByContext(String context);

    List<RuleDefinition> findByEnabledTrue();
}
