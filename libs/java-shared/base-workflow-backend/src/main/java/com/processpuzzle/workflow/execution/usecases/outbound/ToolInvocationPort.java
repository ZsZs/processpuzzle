package com.processpuzzle.workflow.execution.usecases.outbound;

import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolOperation;

import java.util.Map;

/**
 * Invokes one operation of a {@link ToolDefinition} — an outbound HTTP call to an external
 * system. Implemented by {@code RestToolInvocationAdapter} using Spring's {@code RestClient}.
 * Kept as a port (rather than calling {@code RestClient} straight from the use case) so tests can
 * substitute a fake, and so a future non-HTTP tool transport doesn't require touching the
 * execution use cases.
 */
public interface ToolInvocationPort {

    /**
     * @param resolvedPayload already-resolved key/value pairs (from {@code StepDefinition.inputMapping}
     *                        applied against the workflow context) to interpolate into
     *                        {@code operation.payloadTemplate}.
     */
    ToolInvocationResult invoke(ToolDefinition tool, ToolOperation operation, Map<String, Object> resolvedPayload);
}
