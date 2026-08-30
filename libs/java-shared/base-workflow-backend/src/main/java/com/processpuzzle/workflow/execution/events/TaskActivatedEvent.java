package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record TaskActivatedEvent(
        String orgKey, UUID workflowInstanceId, UUID taskInstanceId, String taskDefinitionId) {
}
