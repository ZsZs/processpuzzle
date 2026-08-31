package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/** One operation of a {@link ToolYamlEntry}. {@code method} is an {@code HttpMethod} name. */
public record ToolOperationYaml(
        String id,
        String method,
        String path,
        String description,
        String payloadTemplate,
        List<Integer> expectedStatusCodes
) {
}
