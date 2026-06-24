package com.processpuzzle.rule.usecase;

import java.util.List;

public record EvaluationOutcome(boolean passed, List<RuleViolation> violations) {
}
