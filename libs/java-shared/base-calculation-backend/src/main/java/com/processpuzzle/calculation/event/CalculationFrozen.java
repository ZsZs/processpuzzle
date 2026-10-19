package com.processpuzzle.calculation.event;

import java.time.Instant;
import java.util.UUID;

/** A run's results became immutable; its subject is no longer recalculated. */
public record CalculationFrozen(String orgKey, String calculationId, UUID runId, String subjectType, String subjectId,
                                Instant occurredAt) {
}
