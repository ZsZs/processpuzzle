package com.processpuzzle.workflow.execution.domain;

/**
 * Thrown when a task's preconditionStateKey doesn't match the bound entity's current state.
 * Maps to 409 at the inbound REST adapter, matching base-state's own convention of 409 for
 * "the world moved and your request no longer fits" rather than a plain validation 400 —
 * the task itself was well-formed, it's just not startable yet.
 */
public class TaskPreconditionNotMetException extends RuntimeException {

    private final String taskDefinitionId;
    private final String requiredStateKey;
    private final String actualStateKey;

    public TaskPreconditionNotMetException(String taskDefinitionId, String requiredStateKey, String actualStateKey) {
        super("Task '%s' requires state '%s' but the entity is currently in state '%s'."
                .formatted(taskDefinitionId, requiredStateKey, actualStateKey));
        this.taskDefinitionId = taskDefinitionId;
        this.requiredStateKey = requiredStateKey;
        this.actualStateKey = actualStateKey;
    }

    public String taskDefinitionId() {
        return taskDefinitionId;
    }

    public String requiredStateKey() {
        return requiredStateKey;
    }

    public String actualStateKey() {
        return actualStateKey;
    }
}
