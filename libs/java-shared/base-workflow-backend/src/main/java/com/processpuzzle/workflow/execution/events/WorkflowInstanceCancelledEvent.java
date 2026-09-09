package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record WorkflowInstanceCancelledEvent(
        String orgKey, UUID workflowInstanceId, String workflowId, String reason) {
}
