package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingAssistantAuditLogTest {

    private final Logger audit = (Logger) LoggerFactory.getLogger("com.processpuzzle.ai.assistant.audit");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void attach() {
        events.start();
        audit.addAppender(events);
    }

    @AfterEach
    void detach() {
        audit.detachAppender(events);
    }

    @Test
    void aToolCallIsWrittenWithItsOutcomeAndHashButNoArguments() {
        new LoggingAssistantAuditLog().toolCall(new AssistantAuditLog.ToolCall(Instant.EPOCH, "Ada", "acme",
            "save_draft", "abc123", "ok"));

        assertThat(events.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
            .contains("event=tool", "principal=Ada", "organization=acme", "tool=save_draft",
                "argumentsHash=abc123", "outcome=ok"));
    }

    @Test
    void aTurnIsWrittenWithTheModelAndItsTokens() {
        new LoggingAssistantAuditLog().turn(new AssistantAuditLog.Turn(Instant.EPOCH, "Ada", "acme",
            "pp-designer", 73, 12, 2));

        assertThat(events.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
            .contains("event=turn", "model=pp-designer", "promptTokens=73", "completionTokens=12", "toolCalls=2"));
    }
}
