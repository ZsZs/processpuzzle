package com.processpuzzle.state.adapter.outbound;

import com.processpuzzle.state.usecase.port.GuardResult;
import com.processpuzzle.state.usecase.port.RuleEvaluationPort;
import com.processpuzzle.state.usecase.port.TransitionContext;
import com.processpuzzle.state.usecase.port.TransitionGuard;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A transition guard made of authored business rules rather than code: a transition names it as {@code
 * {beanName: ruleGuard, params: {context: <rule context>}}}, and may fire only when every enabled rule of
 * that context passes against the object's payload. Adding a constraint to a transition is then a rule row,
 * not a new bean — the same promise base-rule makes for forms.
 *
 * <p>A guard naming no {@code context} rejects: it was put on the transition to constrain it, and passing
 * silently would hide the misconfiguration until the constraint mattered.
 */
@Component(RuleTransitionGuard.BEAN_NAME)
public class RuleTransitionGuard implements TransitionGuard {

    public static final String BEAN_NAME = "ruleGuard";
    public static final String CONTEXT_PARAM = "context";

    private final RuleEvaluationPort rules;

    @Autowired
    public RuleTransitionGuard(ObjectProvider<RuleEvaluationPort> rulesProvider) {
        this(rulesProvider.getIfUnique(() -> RuleEvaluationPort.NONE));
    }

    RuleTransitionGuard(RuleEvaluationPort rules) {
        this.rules = rules;
    }

    @Override
    public GuardResult evaluate(TransitionContext context) {
        Object ruleContext = context.guardParams() == null ? null : context.guardParams().get(CONTEXT_PARAM);
        if (ruleContext == null || ruleContext.toString().isBlank()) {
            return GuardResult.rejected("%s on this transition names no '%s' parameter".formatted(BEAN_NAME, CONTEXT_PARAM));
        }
        Map<String, Object> payload = context.entityObject() == null ? Map.of() : context.entityObject().payload();
        return rules.evaluate(context.orgKey(), ruleContext.toString(), payload);
    }
}
