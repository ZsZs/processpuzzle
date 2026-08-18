package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * One step of a {@link TaskDefinition}: ordered, informal guidance for completing the task, not
 * enforced by the engine. When {@code toolId} is set, completing this step triggers a call to the
 * referenced {@link ToolDefinition}'s operation through {@code ToolInvocationPort}; the result is
 * made available to later steps via the process instance context.
 *
 * <p>Stored as a JSONB list on {@link TaskDefinition#getSteps()}, same rationale as
 * {@link TaskIOReference}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {
    private String id;
    private String name;
    private String description;
    private String toolId;
    private String toolOperation;

    /** Tool parameter name -> PPCL expression evaluated against the process context. */
    private Map<String, String> inputMapping;

    /** Process context variable name -> JSONPath expression into the tool response. */
    private Map<String, String> outputMapping;
}
