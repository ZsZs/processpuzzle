package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A {@link ToolDefinition} whose operations one {@link Workflow}'s task steps may invoke.
 *
 * <p>See {@link RoleUse} for why a use is an object rather than a bare id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolUse {

    /** Id of a {@link ToolDefinition} of the same organization. */
    private String toolDefinitionId;
}
