package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

public record TaskYamlEntry(
        String id,
        String name,
        String description,
        String performedBy,
        List<TaskIOReferenceYaml> inputs,
        List<TaskIOReferenceYaml> outputs,
        String preconditionRuleId,
        String postconditionRuleId,
        List<StepYamlEntry> steps,
        List<String> dependsOn,
        Boolean parallel,
        Boolean override
) {
}
