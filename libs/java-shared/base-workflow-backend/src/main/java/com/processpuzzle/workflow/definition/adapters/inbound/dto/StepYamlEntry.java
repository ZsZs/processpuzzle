package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.Map;

/**
 * One step of a task definition. {@code stepType} is a plain string, matched case-insensitively;
 * omitting it means {@code USER_STEP}.
 */
public record StepYamlEntry(
        String id,
        String name,
        String description,
        String stepType,
        String toolDefinitionId,
        String toolOperation,
        Map<String, String> inputMapping,
        Map<String, String> outputMapping
) {
}
