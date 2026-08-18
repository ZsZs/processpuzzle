package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record ProcessInstanceCancelledEvent(
        String orgKey, UUID processInstanceId, String processDefinitionId, String reason) {
}
