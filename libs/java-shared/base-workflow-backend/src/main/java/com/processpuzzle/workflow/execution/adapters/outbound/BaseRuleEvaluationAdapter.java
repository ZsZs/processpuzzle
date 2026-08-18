package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements {@link RuleEvaluationPort} on top of base-rule's {@code EvaluateObject} — the
 * {@code rule :: usecase} named interface declared as an allowed dependency in
 * {@code com.processpuzzle.workflow.package-info}.
 *
 * <p>{@code EvaluateObject} is injected via {@link ObjectProvider}, the same graceful-degradation
 * convention base-app-backend's {@code AppRuleValidator} uses: a host application that assembles
 * processpuzzle-backend without base-rule-backend on the classpath still starts up fine, and every
 * precondition/postcondition check simply always passes rather than failing the whole module.
 */
@Component
public class BaseRuleEvaluationAdapter implements RuleEvaluationPort {

    private final ObjectProvider<EvaluateObject> evaluateObject;

    public BaseRuleEvaluationAdapter(ObjectProvider<EvaluateObject> evaluateObject) {
        this.evaluateObject = evaluateObject;
    }

    @Override
    public RuleCheckResult evaluate(String orgKey, String ruleId, Map<String, Object> context) {
        if (ruleId == null || ruleId.isBlank()) {
            return RuleCheckResult.ALWAYS_PASSES;
        }
        EvaluateObject evaluator = evaluateObject.getIfAvailable();
        if (evaluator == null) {
            return RuleCheckResult.ALWAYS_PASSES;
        }

        // ruleId is used as base-rule's 'context' grouping key -- see RuleEvaluationPort's Javadoc
        // for why a single-rule id lookup isn't available from base-rule's public surface.
        EvaluationOutcome outcome = evaluator.execute(orgKey, ruleId, context == null ? Map.of() : context);
        if (outcome.passed()) {
            return RuleCheckResult.ALWAYS_PASSES;
        }
        String detail = outcome.violations().stream()
                .map(RuleViolation::message)
                .collect(Collectors.joining("; "));
        return new RuleCheckResult(false, detail);
    }
}
