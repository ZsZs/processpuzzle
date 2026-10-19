package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import java.util.List;

/**
 * A valid calculation, ready to evaluate: its decisions in dependency order.
 *
 * @param order the decisions, each after every decision it depends on
 */
public record CalculationPlan(CalculationModel model, List<PlannedDecision> order) {

    public CalculationPlan {
        order = List.copyOf(order);
    }

    public List<String> decisionNames() {
        return order.stream().map(PlannedDecision::name).toList();
    }
}
