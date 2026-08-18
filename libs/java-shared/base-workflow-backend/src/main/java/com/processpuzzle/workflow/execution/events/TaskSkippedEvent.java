package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record TaskSkippedEvent(
        String orgKey, UUID processInstanceId, UUID taskInstanceId, String taskDefinitionId, String reason) {
}
