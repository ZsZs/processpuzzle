package com.processpuzzle.workflow.execution.domain;

/**
 * Run-time state of a {@link TaskInstance}. COMPLETED, SKIPPED and CANCELLED are terminal; only the
 * first two satisfy the tasks and events depending on the task. CANCELLED means the task was
 * interrupted by a boundary event, became unreachable, or its instance was cancelled — see
 * {@link TaskInstance#getCancelReason()}.
 */
public enum TaskInstanceStatus {
    PENDING, ACTIVE, COMPLETED, SKIPPED, BLOCKED, CANCELLED
}
