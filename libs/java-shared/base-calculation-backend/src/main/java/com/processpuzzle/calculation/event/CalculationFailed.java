package com.processpuzzle.calculation.event;

import java.time.Instant;
import java.util.UUID;

/** A run could not compute or write its results; the stored output is unchanged. */
public record CalculationFailed(String orgKey, String calculationId, UUID runId, String subjectType, String subjectId,
                                String error, Instant occurredAt) {
}
