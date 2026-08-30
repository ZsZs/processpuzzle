package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root of a SPEM YAML import/export file: the organization's four definition catalogs and the
 * workflows composed out of them, as five sibling top-level sections.
 *
 * <p>The section names are kebab-case and say <em>definitions</em> explicitly, because a workflow's
 * own {@code roles} / {@code artifacts} / {@code tools} / {@code tasks} are something else — those
 * are uses. Having both spellings in one file is what keeps a reader from having to know which level
 * they are looking at.
 *
 * <p>The definition sections are applied before {@code workflows}, so one file can introduce a
 * workflow and everything it needs — see {@code ImportWorkflowsUseCase}.
 */
public record WorkflowYamlDocument(
        @JsonProperty("role-definitions") List<RoleYamlEntry> roleDefinitions,
        @JsonProperty("artifact-definitions") List<ArtifactYamlEntry> artifactDefinitions,
        @JsonProperty("tool-definitions") List<ToolYamlEntry> toolDefinitions,
        @JsonProperty("task-definitions") List<TaskYamlEntry> taskDefinitions,
        List<WorkflowYamlEntry> workflows
) {
}
