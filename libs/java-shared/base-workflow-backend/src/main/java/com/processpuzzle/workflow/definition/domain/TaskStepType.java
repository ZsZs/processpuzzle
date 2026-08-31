package com.processpuzzle.workflow.definition.domain;

/**
 * Whether completing a {@link StepDefinition} is a human act or a call the engine makes.
 * {@code SERVICE_STEP} is the one that reads {@link StepDefinition#getToolDefinitionId()} /
 * {@link StepDefinition#getToolOperation()}; on a {@code USER_STEP} those are ignored.
 */
public enum TaskStepType {
    USER_STEP, SERVICE_STEP
}
