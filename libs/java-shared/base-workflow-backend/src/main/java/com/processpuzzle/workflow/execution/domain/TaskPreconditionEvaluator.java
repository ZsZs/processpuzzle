package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;

/**
 * The reverse half of the coupling: state gates workflow progression. StartTaskUseCase calls
 * assertStartable before moving a task instance to IN_PROGRESS. A task with no
 * preconditionStateKey is always startable — this only fires for tasks that declared one, so
 * most workflow steps pay nothing for a check they don't need.
 */
public class TaskPreconditionEvaluator {

    private final EntityStateGateway entityStateGateway;

    public TaskPreconditionEvaluator(EntityStateGateway entityStateGateway) {
        this.entityStateGateway = entityStateGateway;
    }

    public void assertStartable(String taskDefinitionId, String preconditionStateKey,
                                 String orgKey, String stateMachineEntityName, String entityInstanceId) {
        if (preconditionStateKey == null) {
            return;
        }

        String actualStateKey = entityStateGateway.currentState(orgKey, stateMachineEntityName, entityInstanceId);
        if (!preconditionStateKey.equals(actualStateKey)) {
            throw new TaskPreconditionNotMetException(taskDefinitionId, preconditionStateKey, actualStateKey);
        }
    }
}
