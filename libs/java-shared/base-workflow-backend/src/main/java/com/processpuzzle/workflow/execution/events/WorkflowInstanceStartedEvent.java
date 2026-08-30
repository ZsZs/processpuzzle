package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record WorkflowInstanceStartedEvent(
        String orgKey, UUID workflowInstanceId, String workflowId, String entityId) {
}
