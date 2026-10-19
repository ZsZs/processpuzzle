package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * One decision of the graph. It has exactly one logic type — a FEEL {@code expr}, a {@code table}, an
 * {@code aggregate} or a {@code rank} — and is evaluated in one scope, which {@code for} names: absent for a
 * single value, an input's name for one value per row, or a grouping attribute ({@code boat}, or
 * {@code finishes.boat} when the input is ambiguous) for one value per group.
 *
 * @param requires what the decision needs; decision names order the graph, any other name documents a column
 */
public record DecisionSpec(
        @JsonProperty("for") String scope,
        List<String> requires,
        String expr,
        String table,
        AggregateSpec aggregate,
        RankSpec rank,
        String description) {

    public DecisionSpec {
        requires = requires == null ? List.of() : List.copyOf(requires);
    }

    /** The logic types the decision declares — exactly one when it is valid. */
    @JsonIgnore
    public List<DecisionKind> kinds() {
        List<DecisionKind> kinds = new ArrayList<>();
        if (expr != null) {
            kinds.add(DecisionKind.EXPR);
        }
        if (table != null) {
            kinds.add(DecisionKind.TABLE);
        }
        if (aggregate != null) {
            kinds.add(DecisionKind.AGGREGATE);
        }
        if (rank != null) {
            kinds.add(DecisionKind.RANK);
        }
        return kinds;
    }

    @JsonIgnore
    public DecisionKind kind() {
        List<DecisionKind> kinds = kinds();
        return kinds.size() == 1 ? kinds.getFirst() : null;
    }
}
