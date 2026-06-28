package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.Severity;

public record RuleViolation(String ruleId, String ruleName, Severity severity, String message, String translocoId) {
}
