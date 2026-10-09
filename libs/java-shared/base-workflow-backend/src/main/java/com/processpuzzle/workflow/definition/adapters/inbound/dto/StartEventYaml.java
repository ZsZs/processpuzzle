package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;
import java.util.Map;

/**
 * One way an instance of a workflow comes into being. {@code startType} selects the mechanism and
 * decides which of the remaining fields carry meaning.
 */
public record StartEventYaml(
        String id,
        String name,
        String startType,
        List<RequiredStartArtifactYaml> requiredArtifacts,
        String eventType,
        Map<String, String> payloadMapping,
        List<String> authorizedRoles,
        String milestoneRef,
        String preconditionExpression,
        TimerYaml timer
) {
}
