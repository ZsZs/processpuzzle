package com.processpuzzle.calculation.engine;

import com.processpuzzle.calculation.domain.definition.DecisionKind;
import com.processpuzzle.calculation.domain.definition.DecisionSpec;
import java.util.Set;

/**
 * A decision as the analyzer resolved it.
 *
 * @param source    a single-valued aggregate's rows: a {@link Scope#row row} or {@link Scope#group group} scope;
 *                  null for every other decision
 * @param dependsOn the decisions it needs evaluated first
 */
public record PlannedDecision(String name, DecisionSpec spec, DecisionKind kind, Scope scope, Scope source,
                              Set<String> dependsOn) {
}
