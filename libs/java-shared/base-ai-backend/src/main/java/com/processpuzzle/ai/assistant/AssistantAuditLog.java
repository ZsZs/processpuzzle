package com.processpuzzle.ai.assistant;

import java.time.Instant;

/**
 * Who asked the assistant to do what, and what came of it. Tool calls and turns are recorded, never the prompts, the
 * designer's text, the drafts or any credential: a tool call carries only a hash of its arguments, so the trail can
 * show that two calls were the same without holding what they said.
 */
public interface AssistantAuditLog {

    /**
     * A tool call and its outcome: {@code ok}, {@code issues}, {@code refused}, {@code refused-role}, {@code stopped-limit}
     * or {@code stopped-repairs}.
     */
    void toolCall(ToolCall call);

    /** A finished turn, with the model that answered and the tokens it used, where the gateway reports them. */
    void turn(Turn turn);

    /**
     * A finished authoring assist: which task, a hash of what was asked (never the text), how many model calls it took,
     * and how it ended: {@code valid}, {@code issues} (the repair rounds ran out) or {@code failed}.
     */
    void assist(Assist assist);

    record Assist(Instant at, String principal, String organization, String task, String inputHash, String model,
                  int rounds, Integer promptTokens, Integer completionTokens, String outcome) {
    }

    record ToolCall(Instant at, String principal, String organization, String tool, String argumentsHash, String outcome) {
    }

    record Turn(Instant at, String principal, String organization, String model,
                Integer promptTokens, Integer completionTokens, int toolCalls) {
    }
}
