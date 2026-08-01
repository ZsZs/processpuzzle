package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import org.springframework.stereotype.Component;

/**
 * Keeps the in-memory {@link RuleEngine} in sync with persisted {@link RuleDefinition}
 * rows as they're created, updated, or deleted.
 *
 * <p>Deliberately minimal for this PoC: registers a rule's raw expression under its own
 * tenant-scoped key, nothing more. Resolving the <em>effective</em> expression for a given entity —
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
        RuleKey key = RuleKey.of(rule.getOrgKey(), rule.getId());
        if (rule.isEnabled()) {
            ruleEngine.registerRule(key, rule.getExpression());
        } else {
            ruleEngine.unregisterRule(key);
        }
    }

    public void unregister(String orgKey, String ruleId) {
        ruleEngine.unregisterRule(RuleKey.of(orgKey, ruleId));
    }
}
