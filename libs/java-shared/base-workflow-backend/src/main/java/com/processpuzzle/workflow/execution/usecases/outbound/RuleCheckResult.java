package com.processpuzzle.workflow.execution.usecases.outbound;

/**
 * Result of evaluating a task's precondition or postcondition rule. {@code detail} carries the
 * violation message when {@code passed} is false, surfaced as {@code TaskInstance.blockedReason}
 * or {@code CompleteTaskResponse.postconditionDetail}.
 */
public record RuleCheckResult(boolean passed, String detail) {

    public static final RuleCheckResult ALWAYS_PASSES = new RuleCheckResult(true, null);
}
