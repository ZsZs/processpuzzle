package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.domain.definition.DecisionKind;

/**
 * The outcome of one decision: its value — a single value, the list of per-row values, or per group the group's
 * attribute value with the decision's — or, for the decision a calculation failed on, the error.
 */
public record TraceEntry(String decision, DecisionKind kind, String scope, Object value, String error) {
}
