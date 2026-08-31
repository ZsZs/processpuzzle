package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/**
 * One artifact definition of a {@link WorkflowYamlDocument}'s {@code artifact-definitions} section.
 * {@code artifactType} is a plain string, matched case-insensitively, so a hand-edited seed file may
 * write {@code entity} for {@code ENTITY}.
 */
public record ArtifactYamlEntry(
        String id,
        String name,
        String description,
        String artifactType,
        String artifactTypeId,
        String stateMachineId
) {
}
