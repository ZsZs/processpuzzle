package com.processpuzzle.workflow.execution.domain;

/**
 * Published by CompleteTaskUseCase once a task instance has finished. Carries everything the
 * state-trigger listener needs already resolved — the entity name/id the task's output work
 * product is bound to, and the trigger key from the task's TaskDefinition — so the listener
 * does no further lookups and stays a thin translation from event to gateway call.
 * <p>
 * {@code completionStateTriggerKey} is null for the (common) case of a task with no state-machine
 * side effect; the listener no-ops on null rather than the publisher filtering, so every completed
 * task is visible on the event log regardless of whether it drives state.
 */
public record TaskCompletedEvent(
        String orgKey,
        String workflowInstanceId,
        String taskDefinitionId,
        String completionStateTriggerKey,
        String stateMachineEntityName,
        String entityInstanceId
) {}
