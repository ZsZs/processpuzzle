package com.processpuzzle.composition;

import com.processpuzzle.baseentity.port.EntityRulesPort;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.RuleViolation;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Guards base-entity's saves with base-rule: an object may be saved when no ERROR rule of its entity's context
 * is violated. Warnings and infos do not block, exactly as they do not block a form save or a state transition
 * (see {@link StateRuleAdapter}). Because base-entity asks on every create and update, a rule holds whichever path
 * the change took — the form, an embedded child's save of the whole aggregate, or a workflow step.
 */
@Component
public class EntityRulesAdapter implements EntityRulesPort {

    private final EvaluateObject evaluateObject;

    public EntityRulesAdapter(EvaluateObject evaluateObject) {
        this.evaluateObject = evaluateObject;
    }

    @Override
    public List<String> blockingViolationsOf(String orgKey, String entityName, Map<String, Object> payload) {
        return evaluateObject.execute(orgKey, entityName, payload == null ? Map.of() : payload).violations().stream()
                .filter(violation -> violation.severity() == Severity.ERROR)
                .map(EntityRulesAdapter::messageOf)
                .toList();
    }

    /** A violation's message, or its rule's name when the rule was authored without one. */
    private static String messageOf(RuleViolation violation) {
        String message = violation.message();
        return message == null || message.isBlank() ? violation.ruleName() : message;
    }
}
