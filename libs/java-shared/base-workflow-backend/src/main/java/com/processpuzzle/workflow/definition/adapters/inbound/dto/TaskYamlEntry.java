package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/**
 * One task definition of a {@link WorkflowYamlDocument}'s {@code task-definitions} section.
 * {@code inputs} and {@code outputs} name artifact definition ids.
 */
public record TaskYamlEntry(
        String id,
        String name,
        String description,
        List<String> performedByRoles,
        List<String> inputs,
        List<String> outputs,
        String preconditionRuleId,
        String postconditionRuleId,
        List<StepYamlEntry> steps
) {
}
