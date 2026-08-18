package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record ProcessInstanceStartedEvent(
        String orgKey, UUID processInstanceId, String processDefinitionId, String entityId) {
}
