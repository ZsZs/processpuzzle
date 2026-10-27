package com.processpuzzle.ai.assistant;

import java.time.Instant;

/**
 * Who asked the assistant to do what, and what came of it. Tool calls and turns are recorded, never the prompts, the
 * designer's text, the drafts or any credential: a tool call carries only a hash of its arguments, so the trail can
 * show that two calls were the same without holding what they said.
 */
public interface AssistantAuditLog {

    /** A tool call and its outcome: {@code ok}, {@code issues}, {@code refused}, {@code refused-role} or {@code stopped-limit}. */
    void toolCall(ToolCall call);

    /** A finished turn, with the model that answered and the tokens it used, where the gateway reports them. */
    void turn(Turn turn);

    record ToolCall(Instant at, String principal, String organization, String tool, String argumentsHash, String outcome) {
    }

    record Turn(Instant at, String principal, String organization, String model,
                Integer promptTokens, Integer completionTokens, int toolCalls) {
    }
}
