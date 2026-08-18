package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One process entry as it appears in a SPEM YAML file. {@code extends} is a Java keyword, so the
 * YAML key is remapped to {@code extendsProcessId} via {@link JsonProperty} — same reasoning as
 * {@code RuleYamlEntry.extendsRuleId} in base-rule-backend. Kept independent from the generated
 * {@code ProcessDefinitionInput} model (whose own handling of the {@code extends} field name is
 * uncertain without running the generator — see the module README) so import/export logic isn't
 * coupled to that uncertainty.
 */
public record ProcessYamlEntry(
        String id,
        String name,
        String description,
        @JsonProperty("extends") String extendsProcessId,
        List<String> tools,
        List<RoleYamlEntry> roles,
        List<WorkProductYamlEntry> workProducts,
        List<TaskYamlEntry> tasks
) {
}
