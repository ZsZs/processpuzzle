package com.processpuzzle.rule.usecase.service;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    /**
     * {@link #register} once the surrounding transaction commits, or right away when there is none.
     *
     * <p>For a write that may still be rolled back: a Business Starter dry run applies the import in
     * a transaction that is then rolled back, and an engine registered mid-transaction would keep
     * rules the database never stored.
     */
    public void registerAfterCommit(RuleDefinition rule) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            register(rule);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                register(rule);
            }
        });
    }

    public void unregister(String orgKey, String ruleId) {
        ruleEngine.unregisterRule(RuleKey.of(orgKey, ruleId));
    }
}
