package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

/**
 * Published when an interrupting boundary event fired and cancelled the ACTIVE task it is attached to.
 *
 * @param eventUseId the boundary event that interrupted the task
 */
public record TaskInterruptedEvent(String orgKey, UUID workflowInstanceId, UUID taskInstanceId,
                                   String taskDefinitionId, String eventUseId) {
}
