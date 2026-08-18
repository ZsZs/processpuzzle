package com.processpuzzle.workflow.definition.adapters.inbound.dto;

public record WorkProductYamlEntry(
        String id,
        String name,
        String description,
        String type,
        String entityTypeId,
        String stateMachineId
) {
}
