package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;
import java.util.Map;

/**
 * One intermediate event of a workflow. {@code direction} and {@code joinType} are plain strings, like
 * every other enum-valued field of the YAML dialect. {@code interrupting} is boxed so that an absent
 * value — the default, true — stays absent on export.
 */
public record EventUseYaml(
        String id,
        String name,
        String eventDefinitionId,
        String direction,
        List<String> dependsOn,
        String joinType,
        String correlationKey,
        Map<String, String> payloadMapping,
        TimerYaml timer,
        String attachedTo,
        Boolean interrupting
) {
}
