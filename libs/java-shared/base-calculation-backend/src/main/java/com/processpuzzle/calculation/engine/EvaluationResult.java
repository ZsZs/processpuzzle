package com.processpuzzle.calculation.engine;

import java.util.List;
import java.util.Map;

/**
 * What a calculation computed: every single-valued decision, every input's rows with the row decisions as added
 * columns, every grouping's rows with the group decisions, the evaluation order and the trace. Values are the
 * engine's own — {@link java.math.BigDecimal}, {@code java.time} — until {@link #plain()} turns them into JSON.
 */
public record EvaluationResult(Map<String, Object> values, Map<String, List<Map<String, Object>>> rows,
                               Map<String, List<Map<String, Object>>> groups, List<String> order, List<TraceEntry> trace) {

    /** The result as JSON-ready maps, lists, numbers and strings. */
    public EvaluationResult plain() {
        return new EvaluationResult(Values.plainMap(values), plainTables(rows), plainTables(groups), order,
                trace.stream().map(entry -> new TraceEntry(entry.decision(), entry.kind(), entry.scope(),
                        Values.plain(entry.value()), entry.error())).toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> plainTables(Map<String, List<Map<String, Object>>> tables) {
        Map<String, List<Map<String, Object>>> plain = new java.util.LinkedHashMap<>();
        tables.forEach((name, table) -> plain.put(name, table.stream().map(row -> (Map<String, Object>) Values.plain(row)).toList()));
        return plain;
    }
}
