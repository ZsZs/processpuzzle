package com.processpuzzle.calculation.engine;

import java.util.List;

/**
 * What {@link CalculationAnalyzer} found: the errors, the decisions with their kind and scope, the dependency
 * edges, and — when there are no errors — the plan.
 */
public record CalculationAnalysis(List<String> errors, List<PlannedDecision> decisions, List<Dependency> dependencies,
                                  CalculationPlan plan) {

    /** {@code to} requires {@code from}. */
    public record Dependency(String from, String to) {
    }

    public CalculationAnalysis {
        errors = List.copyOf(errors);
        decisions = List.copyOf(decisions);
        dependencies = List.copyOf(dependencies);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** The evaluation order, empty unless the calculation is valid. */
    public List<String> order() {
        return plan == null ? List.of() : plan.decisionNames();
    }
}
