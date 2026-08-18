package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.Map;

public record StepYamlEntry(
        String id,
        String name,
        String description,
        String toolId,
        String toolOperation,
        Map<String, String> inputMapping,
        Map<String, String> outputMapping
) {
}
