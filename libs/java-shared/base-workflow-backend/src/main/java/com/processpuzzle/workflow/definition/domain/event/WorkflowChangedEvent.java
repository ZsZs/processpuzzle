package com.processpuzzle.workflow.definition.domain.event;

/**
 * Published when a workflow was created, replaced, imported or deleted. The execution layer observes it
 * to keep the schedules of the workflow's TIME_BASED_PRECONDITION start events in step with the
 * definition.
 *
 * <p>Observe with {@code @TransactionalEventListener} and {@code REQUIRES_NEW}: before commit the write
 * can still be refused, and after commit a {@code REQUIRED} write would join the finished transaction
 * and be discarded.
 *
 * @param orgKey     the organization the workflow belongs to
 * @param workflowId the workflow's id
 * @param deleted    whether the workflow is gone
 */
public record WorkflowChangedEvent(String orgKey, String workflowId, boolean deleted) {
}
