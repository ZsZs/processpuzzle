package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/**
 * A catalog tool. Tools were already standalone before the catalog existed, but they appear in the
 * document all the same: a seed file that gives a task a step without also giving that step's tool
 * would not survive its own import, because the workflow validator insists every referenced tool
 * exists.
 */
public record ToolYamlEntry(
        String id,
        String name,
        String description,
        String baseUrl,
        ToolAuthYaml auth,
        List<ToolOperationYaml> operations
) {
}
