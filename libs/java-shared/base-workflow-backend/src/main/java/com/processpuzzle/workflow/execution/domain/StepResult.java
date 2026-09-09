package com.processpuzzle.workflow.execution.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Outcome of one {@code StepDefinition} within a {@link TaskInstance}. Stored as a JSONB list on
 * {@link TaskInstance#getStepResults()} — same rationale as the JSONB value types in the
 * definition layer (see {@code TaskIOReference}'s Javadoc).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    private String stepId;
    private Instant completedAt;
    private Map<String, Object> toolResponse;
    private String error;
}
