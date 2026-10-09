package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/** The state a task expects one of its inputs in, and leaves one of its outputs in. */
public record TaskArtifactStateYaml(String artifactDefinitionId, String inputState, String outputState) {
}
