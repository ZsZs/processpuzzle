package com.processpuzzle.state.usecase.port;

import java.util.Map;

/**
 * Evaluates the business rules authored for a rule context against an object's payload — what {@code
 * RuleTransitionGuard} asks before a transition may fire. base-state names no rule engine here; the
 * application's composition root supplies the adapter.
 *
 * <p>Without one every rule passes, so a machine naming {@code ruleGuard} still runs where no rule engine is
 * deployed: a port that cannot answer must not answer "no".
 */
public interface RuleEvaluationPort {

    RuleEvaluationPort NONE = (orgKey, ruleContext, subject) -> GuardResult.allowed();

    /**
     * @param ruleContext the context the rules were authored under; every enabled rule of it must pass
     * @param subject     the object the rules are evaluated against — the entity object's payload
     * @return allowed, or rejected with the violations' messages as the reason
     */
    GuardResult evaluate(String orgKey, String ruleContext, Map<String, Object> subject);
}
