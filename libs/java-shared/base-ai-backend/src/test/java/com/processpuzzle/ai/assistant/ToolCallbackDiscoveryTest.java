package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;

class ToolCallbackDiscoveryTest {

    @Test
    void theDraftToolsAreExposedToTheModel() {
        List<ToolCallback> callbacks = List.of(ToolCallbacks.from(new DraftTools(null, null, 1, 3, new LoggingAssistantAuditLog())));

        assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
            .containsExactlyInAnyOrder("list_definitions", "get_definition", "get_schema", "save_draft", "validate_draft");
    }
}
