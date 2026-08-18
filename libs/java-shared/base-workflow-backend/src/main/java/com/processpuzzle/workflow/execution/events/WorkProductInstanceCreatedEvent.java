package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

/**
 * Published for every {@code WorkProductInstance} created at process start. A consumer that owns
 * base-state integration (today: nobody — see the package Javadoc's "known gap") would listen for
 * this to initialize a state machine instance when {@code stateMachineId} is set, then report the
 * resulting instance id back so it can be recorded as {@code WorkProductInstance.stateMachineInstanceId}.
 * That callback path doesn't exist yet either; this event is the first half of it.
 */
public record WorkProductInstanceCreatedEvent(
        String orgKey, UUID processInstanceId, UUID workProductInstanceId,
        String workProductDefinitionId, String stateMachineId, String entityId) {
}
