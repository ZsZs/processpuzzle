package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/** Auth block of a {@link ToolYamlEntry}. {@code type} is an {@code AuthType} name. */
public record ToolAuthYaml(String type, String secretRef) {
}
