package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One workflow of a {@link WorkflowYamlDocument}: its identity, its start events, its uses of the
 * file's (or the organization's) definitions, and its intermediate events.
 *
 * <p>{@code extends} is a Java reserved word, hence the {@link JsonProperty} rename — the same
 * accommodation the generated OpenAPI model makes.
 */
public record WorkflowYamlEntry(
        String id,
        String name,
        String description,
        @JsonProperty("extends") String extendsWorkflowId,
        List<StartEventYaml> startEvents,
        List<RoleUseYaml> roles,
        List<ArtifactUseYaml> artifacts,
        List<ToolUseYaml> tools,
        List<TaskUseYaml> tasks,
        List<EventUseYaml> events
) {
}
