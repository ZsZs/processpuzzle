package com.processpuzzle.workflow.definition.adapters.inbound.dto;

import java.util.List;

/** Root of a SPEM process-definitions YAML file. Mirrors {@code RuleYamlDocument}'s shape. */
public record ProcessYamlDocument(List<ProcessYamlEntry> processes) {
}
