package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * Competition ranking (1, 2, 2, 4) of the rows of a scope.
 *
 * @param by          the column ranked by
 * @param order       {@code asc} (the default — lowest first) or {@code desc}
 * @param partitionBy a column; ranks restart in each partition
 * @param filter      a FEEL condition over a row; a row failing it gets no rank
 * @param tieBreak    columns that order rows equal on {@code by}, each optionally followed by {@code asc} or
 *                    {@code desc}; rows equal on all of them share a rank
 */
public record RankSpec(String by, String order, String partitionBy, String filter, List<String> tieBreak) {

    public RankSpec {
        tieBreak = tieBreak == null ? List.of() : List.copyOf(tieBreak);
    }

    @JsonIgnore
    public boolean descending() {
        return "desc".equalsIgnoreCase(order);
    }
}
