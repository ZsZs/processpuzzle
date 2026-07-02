package com.processpuzzle.rule.adapter.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One rule entry as it appears in a PPCL YAML file. {@code extends} is a Java keyword, so
 * the YAML key is remapped to {@code extendsRuleId} via {@link JsonProperty}.
 */
public record RuleYamlEntry(
        String id,
        String name,
        String description,
        String context,
        String expression,
        String severity,
        String message,
        String translocoId,
        @JsonProperty("extends") String extendsRuleId,
        Boolean override,
        Boolean enabled,
        List<String> fields
) {
}
