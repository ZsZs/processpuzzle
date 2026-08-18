package com.processpuzzle.workflow.execution.events;

import java.util.UUID;

public record ProcessInstanceCompletedEvent(String orgKey, UUID processInstanceId, String processDefinitionId) {
}
