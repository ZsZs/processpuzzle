package com.processpuzzle.calculation.engine;

import java.util.List;

/**
 * A calculation that could not be evaluated: a decision failed, or a table's hit policy was violated. Carries the
 * trace up to and including the failing decision, so a failed run still shows how far it got.
 */
public class CalculationException extends RuntimeException {

    private final transient List<TraceEntry> trace;

    public CalculationException(String message, List<TraceEntry> trace) {
        super(message);
        this.trace = trace == null ? List.of() : List.copyOf(trace);
    }

    public List<TraceEntry> trace() {
        return trace;
    }
}
