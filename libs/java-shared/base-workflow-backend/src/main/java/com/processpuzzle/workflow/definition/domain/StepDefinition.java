package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * One step of a {@link TaskDefinition}. A {@link TaskStepType#USER_STEP} is ordered, informal
 * guidance for completing the task and is not enforced by the engine; a
 * {@link TaskStepType#SERVICE_STEP} names a tool operation, and completing it triggers a call to
 * the referenced {@link ToolDefinition} through {@code ToolInvocationPort} whose result is made
 * available to later steps via the workflow instance context.
 *
 * <p>Stored as a JSONB list on {@link TaskDefinition#getSteps()}, same rationale as
 * {@link TaskUse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDefinition {
    private String id;
    private String name;
    private String description;

    /** Null is read as {@link TaskStepType#USER_STEP}. */
    @Builder.Default
    private TaskStepType stepType = TaskStepType.USER_STEP;

    /** Id of a {@link ToolDefinition} of the same organization. Only read on a SERVICE_STEP. */
    private String toolDefinitionId;

    /** Operation id within the referenced tool definition. */
    private String toolOperation;

    /** Tool parameter name -> PPCL expression evaluated against the workflow context. */
    private Map<String, String> inputMapping;

    /** Workflow context variable name -> JSONPath expression into the tool response. */
    private Map<String, String> outputMapping;
}
