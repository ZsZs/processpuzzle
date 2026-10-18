package com.processpuzzle.calculation.domain.definition;

import java.util.List;

/**
 * A decision table: each input is a FEEL expression evaluated in the deciding scope, each rule cell a FEEL
 * unary test against that input ({@code -} matches anything), and each rule's {@code then} a FEEL expression.
 *
 * @param output the name of what the table yields; informational, the deciding decision names the result
 */
public record DecisionTable(HitPolicy hitPolicy, List<String> inputs, String output, List<TableRule> rules) {

    public DecisionTable {
        hitPolicy = hitPolicy == null ? HitPolicy.FIRST : hitPolicy;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
