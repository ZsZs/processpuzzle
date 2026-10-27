package com.processpuzzle.ai.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the audit trail as structured log lines on the {@code com.processpuzzle.ai.assistant.audit} logger, so that a
 * log pipeline can keep, route and retain them apart from the application's own log. It is the default; a durable
 * store is an {@link AssistantAuditLog} bean the application declares.
 */
public class LoggingAssistantAuditLog implements AssistantAuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.processpuzzle.ai.assistant.audit");

    @Override
    public void toolCall(ToolCall call) {
        AUDIT.info("event=tool at={} principal={} organization={} tool={} argumentsHash={} outcome={}",
            call.at(), call.principal(), call.organization(), call.tool(), call.argumentsHash(), call.outcome());
    }

    @Override
    public void turn(Turn turn) {
        AUDIT.info("event=turn at={} principal={} organization={} model={} promptTokens={} completionTokens={} toolCalls={}",
            turn.at(), turn.principal(), turn.organization(), turn.model(),
            turn.promptTokens(), turn.completionTokens(), turn.toolCalls());
    }
}
