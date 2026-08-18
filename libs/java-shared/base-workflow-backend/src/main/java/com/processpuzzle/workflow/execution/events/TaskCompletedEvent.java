package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record TaskCompletedEvent(
        String orgKey, UUID processInstanceId, UUID taskInstanceId, String taskDefinitionId) {
}
