package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;
import java.util.Map;

/**
 * How an instance of a workflow comes into being. {@code startType} selects the mechanism and
 * decides which of the remaining fields carry meaning.
 */
public record StartConditionYaml(
        String startType,
        List<RequiredStartArtifactYaml> requiredArtifacts,
        String eventType,
        Map<String, String> payloadMapping,
        List<String> authorizedRoles,
        String milestoneRef,
        String preconditionExpression
) {
}
