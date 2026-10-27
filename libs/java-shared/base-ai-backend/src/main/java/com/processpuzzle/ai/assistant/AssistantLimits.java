package com.processpuzzle.ai.assistant;

/**
 * How far one turn of the assistant may go: the tool calls it may make, and how many times one draft may come back
 * with issues before the assistant must report it instead.
 */
public record AssistantLimits(int maxToolCalls, int maxRepairRounds) {
}
