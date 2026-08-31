package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Map;

/**
 * Evaluates a single named rule from base-rule against a workflow instance's context. Implemented
 * by {@code BaseRuleEvaluationAdapter}, which wraps {@code rule.usecase.EvaluateObject} — the
 * only rule-evaluation entry point base-rule exposes as a named interface (see
 * {@code com.processpuzzle.workflow.package-info}).
 *
 * <p><b>Design note:</b> base-workflow-api.yaml's {@code preconditionRuleId}/
 * {@code postconditionRuleId} name a single rule, but {@code EvaluateObject.execute(orgKey,
 * context, entity)} evaluates every enabled rule registered under a {@code context} string, not
 * one rule by id — that's the only evaluation entry point base-rule exposes (confirmed by reading
 * both {@code EvaluateObject} and how base-app-backend's {@code AppRuleValidator} already uses
 * it: a single fixed context string covering a whole governance point, e.g.
 * {@code "app-definition-validation"}). This module adopts the same convention: the
 * precondition/postcondition rule for a task is authored in base-rule with its {@code context}
 * field set to the ruleId referenced in the task definition. If more than one rule shares that
 * context, all of them must pass.
 */
public interface RuleEvaluationPort {

    /**
     * @param ruleId the value of {@code TaskDefinition.preconditionRuleId} or
     *               {@code .postconditionRuleId} — used as base-rule's {@code context} parameter,
     *               see class Javadoc.
     * @param context the workflow instance's current context map, passed as the "entity" being
     *                evaluated.
     * @return {@link RuleCheckResult#ALWAYS_PASSES} when {@code ruleId} is null (no rule
     *         configured for this gate) or when base-rule isn't wired into the host application.
     */
    RuleCheckResult evaluate(String orgKey, String ruleId, Map<String, Object> context);
}
