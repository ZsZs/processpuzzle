package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.springframework.stereotype.Component;

@Component
public class RuleExtendsValidator {

    private static final int MAX_EXTENDS_CHAIN_DEPTH = 100;

    private final RuleDefinitionRepository repository;

    public RuleExtendsValidator(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public void validate(String ownId, String extendsRuleId) {
        if (extendsRuleId == null) {
            return;
        }
        if (extendsRuleId.equals(ownId)) {
            throw new IllegalArgumentException("Rule cannot extend itself: " + ownId);
        }

        RuleDefinition parent = repository.findById(extendsRuleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rule '" + ownId + "' extends unknown rule '" + extendsRuleId + "'"));

        String cursor = parent.getExtendsRuleId();
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(ownId)) {
                throw new IllegalArgumentException(
                        "Setting '" + ownId + "' to extend '" + extendsRuleId
                                + "' would create a cycle");
            }
            if (++depth > MAX_EXTENDS_CHAIN_DEPTH) {
                throw new IllegalStateException(
                        "Extends chain too deep (or already cyclic) starting at '" + cursor + "'");
            }
            cursor = repository.findById(cursor).map(RuleDefinition::getExtendsRuleId).orElse(null);
        }
    }
}
