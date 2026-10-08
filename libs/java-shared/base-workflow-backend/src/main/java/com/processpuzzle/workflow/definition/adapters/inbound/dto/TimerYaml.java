package com.processpuzzle.workflow.definition.adapters.inbound.dto;

/** When a timer fires; {@code type} is a plain string, like every other enum-valued field of the dialect. */
public record TimerYaml(String type, String expression) {
}
