package com.processpuzzle.calculation.domain.definition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An aggregate over rows: exactly one of {@code count}, {@code countDistinct}, {@code sum}, {@code avg},
 * {@code min} and {@code max}, each naming the column (or FEEL expression over a row) it aggregates.
 *
 * @param partitionBy a column; in a row scope the aggregate is computed per partition and broadcast to its rows
 * @param dropWorst   how many of the worst values to leave out — a number, or a FEEL expression such as a
 *                    decision's name; the series discard
 * @param dropBest    how many of the best values to leave out
 * @param better      which values are better, {@code lower} (the default — points, ranks, times) or {@code higher}
 * @param of          the rows a single-valued aggregate runs over — an input or a grouping attribute — when its
 *                    requirements do not tell
 */
public record AggregateSpec(
        String count,
        String countDistinct,
        String sum,
        String avg,
        String min,
        String max,
        String partitionBy,
        Object dropWorst,
        Object dropBest,
        String better,
        String of) {

    /** The aggregate functions declared, by name — exactly one when it is valid. */
    @JsonIgnore
    public Map<String, String> functions() {
        Map<String, String> functions = new LinkedHashMap<>();
        put(functions, "count", count);
        put(functions, "countDistinct", countDistinct);
        put(functions, "sum", sum);
        put(functions, "avg", avg);
        put(functions, "min", min);
        put(functions, "max", max);
        return functions;
    }

    @JsonIgnore
    public boolean higherIsBetter() {
        return "higher".equalsIgnoreCase(better);
    }

    private static void put(Map<String, String> functions, String name, String operand) {
        if (operand != null) {
            functions.put(name, operand);
        }
    }
}
