package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/** One artifact an INPUT_ARTIFACT start condition waits for, and optionally the state it needs. */
public record RequiredStartArtifactYaml(String artifactDefinitionId, String state) {
}
