package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record TaskCompletedEvent(
        String orgKey, UUID workflowInstanceId, UUID taskInstanceId, String taskDefinitionId) {
}
