package com.processpuzzle.workflow.access;

import java.util.UUID;

/**
 * One workflow instance as another feature may see it.
 *
 * @param status ACTIVE, SUSPENDED, COMPLETED or CANCELLED
 */
public record WorkflowInstanceView(UUID id, String workflowId, String status) {
}
