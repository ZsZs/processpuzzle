package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record WorkflowInstanceCompletedEvent(String orgKey, UUID workflowInstanceId, String workflowId) {
}
