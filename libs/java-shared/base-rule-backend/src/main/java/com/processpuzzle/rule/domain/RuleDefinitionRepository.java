package com.processpuzzle.rule.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, String> {

    List<RuleDefinition> findByContext(String context);

    List<RuleDefinition> findByEnabledTrue();
}
