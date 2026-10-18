package com.processpuzzle.calculation.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A run computed its results and wrote its output.
 *
 * @param values the single-valued decisions, as JSON-ready values
 */
public record CalculationCompleted(String orgKey, String calculationId, UUID runId, String subjectType, String subjectId,
                                   Map<String, Object> values, Instant occurredAt) {
}
