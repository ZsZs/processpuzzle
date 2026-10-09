package com.processpuzzle.composition;

import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import com.processpuzzle.state.usecase.port.GuardResult;
import com.processpuzzle.state.usecase.port.RuleEvaluationPort;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Answers base-state's {@code ruleGuard} from base-rule: a transition may fire when no ERROR rule of the
 * guard's context is violated by the object's payload. Warnings and infos do not block, exactly as they do
 * not block a form save; the reason a transition is refused is the ERROR violations' messages.
 */
@Component
public class StateRuleAdapter implements RuleEvaluationPort {

    private final EvaluateObject evaluateObject;

    public StateRuleAdapter(EvaluateObject evaluateObject) {
        this.evaluateObject = evaluateObject;
    }

    @Override
    public GuardResult evaluate(String orgKey, String ruleContext, Map<String, Object> subject) {
        EvaluationOutcome outcome = evaluateObject.execute(orgKey, ruleContext, subject == null ? Map.of() : subject);
        if (outcome == null || outcome.passed()) {
            return GuardResult.allowed();
        }
        String reason = outcome.violations().stream()
                .filter(violation -> violation.severity() == Severity.ERROR)
                .map(StateRuleAdapter::reasonOf)
                .collect(Collectors.joining("; "));
        return GuardResult.rejected(reason);
    }

    /** A violation's message, or its rule's name when the rule was authored without one. */
    private static String reasonOf(RuleViolation violation) {
        String message = violation.message();
        return message == null || message.isBlank() ? violation.ruleName() : message;
    }
}
