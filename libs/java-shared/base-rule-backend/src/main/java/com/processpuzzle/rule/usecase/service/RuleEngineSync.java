package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import org.springframework.stereotype.Component;

/**
 * Keeps the in-memory {@link RuleEngine} in sync with persisted {@link RuleDefinition}
 * rows as they're created, updated, or deleted.
 *
 * <p>Deliberately minimal for this PoC: registers a rule's raw expression under its own
 * id, nothing more. Resolving the <em>effective</em> expression for a given entity —
 * walking the {@code extends}/{@code override} chain and reconciling it with
 * ProcessPuzzle's actual entity-type hierarchy (so a rule on {@code Order} is known to also
 * apply to {@code SpecialOrder} unless overridden) — is intentionally left out here. That
 * needs details of how the entity metadata models supertypes, which this module doesn't
 * have visibility into yet; flagged as follow-up work.
 */
@Component
public class RuleEngineSync {

    private final RuleEngine ruleEngine;

    public RuleEngineSync(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public void register(RuleDefinition rule) {
        if (rule.isEnabled()) {
            ruleEngine.registerRule(rule.getId(), rule.getExpression());
        } else {
            ruleEngine.unregisterRule(rule.getId());
        }
    }

    public void unregister(String ruleId) {
        ruleEngine.unregisterRule(ruleId);
    }
}
