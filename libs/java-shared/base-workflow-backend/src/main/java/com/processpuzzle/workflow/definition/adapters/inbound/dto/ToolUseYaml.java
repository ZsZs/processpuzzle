package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/** A tool definition whose operations one workflow's task steps may invoke. */
public record ToolUseYaml(String toolDefinitionId) {
}
