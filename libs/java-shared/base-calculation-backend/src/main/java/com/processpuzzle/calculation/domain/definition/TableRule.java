package com.processpuzzle.calculation.domain.definition;

import java.util.List;
import java.util.Objects;

/**
 * One rule of a {@link DecisionTable}. YAML reads {@code 0} or {@code 0.10} as numbers, so both sides are kept as
 * read and turned into FEEL text by {@link #cells()} and {@link #outputExpression()}.
 */
public record TableRule(List<Object> when, Object then) {

    public static final String ANY = "-";

    /** The unary tests, one per table input; {@code -} for a blank cell. */
    public List<String> cells() {
        return when == null ? List.of() : when.stream().map(cell -> cell == null ? ANY : cell.toString().trim()).toList();
    }

    /** The FEEL expression of the rule's output, or null when the rule has none. */
    public String outputExpression() {
        return then == null ? null : Objects.toString(then).trim();
    }
}
