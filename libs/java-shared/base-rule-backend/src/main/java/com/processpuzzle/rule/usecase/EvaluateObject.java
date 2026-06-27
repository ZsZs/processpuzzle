package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class EvaluateObject {

    private final RuleDefinitionRepository repository;
    private final RuleEngine ruleEngine;

    public EvaluateObject(RuleDefinitionRepository repository, RuleEngine ruleEngine) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
    }

    public EvaluationOutcome execute(String context, Map<String, Object> entity) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("context is required");
        }
        if (entity == null) {
            throw new IllegalArgumentException("entity is required");
        }

        List<RuleViolation> violations = new ArrayList<>();
        for (RuleDefinition rule : repository.findByContext(context)) {
            if (!rule.isEnabled()) {
                continue;
            }
            // Lazily register so a freshly-restarted instance can evaluate before its in-memory
            // engine state has been re-synced from the database.
            if (!ruleEngine.isRegistered(rule.getId())) {
                ruleEngine.registerRule(rule.getId(), rule.getExpression());
            }
            try {
                if (!ruleEngine.evaluate(rule.getId(), entity)) {
                    violations.add(new RuleViolation(
                            rule.getId(), rule.getName(), rule.getSeverity(),
                            messageFor(rule), rule.getTranslocoId()));
                }
            } catch (RuntimeException ex) {
                violations.add(new RuleViolation(
                        rule.getId(), rule.getName(), Severity.ERROR,
                        "Evaluation failed: " + ex.getMessage(), rule.getTranslocoId()));
            }
        }

        boolean passed = violations.stream().noneMatch(v -> v.severity() == Severity.ERROR);
        return new EvaluationOutcome(passed, violations);
    }

    private static String messageFor(RuleDefinition rule) {
        if (rule.getMessage() != null && !rule.getMessage().isBlank()) {
            return rule.getMessage();
        }
        if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
            return rule.getDescription();
        }
        return rule.getName();
    }
}
