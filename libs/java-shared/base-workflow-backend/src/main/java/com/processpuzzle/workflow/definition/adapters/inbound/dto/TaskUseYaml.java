package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/**
 * One task's place in one workflow. {@code joinType} is a plain string here, like every other
 * enum-valued field of the YAML dialect, so a hand-edited seed file may write {@code any} for
 * {@code ANY}.
 */
public record TaskUseYaml(
        String taskDefinitionId,
        String performedBy,
        List<String> dependsOn,
        String joinType,
        Boolean parallel,
        Boolean override
) {
}
