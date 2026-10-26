package com.processpuzzle.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Phase 0 spike of docs/ai/implementation-plan.md: one prompt through Spring AI and the LiteLLM gateway to the
 * model, and one {@code @Tool} method executed. Throwaway: it spends real tokens, so it only runs when
 * {@code PP_LLM_GATEWAY_URL} is set. Run it against stage through an SSH tunnel:
 * <pre>
 * ssh -L 4000:127.0.0.1:4000 &lt;stage-host&gt;
 * PP_LLM_GATEWAY_URL=http://localhost:4000 PP_LLM_GATEWAY_KEY=&lt;master key&gt; \
 *   mvn -pl libs/java-shared/base-ai-backend test -Dtest=LlmGatewaySmokeTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "PP_LLM_GATEWAY_URL", matches = ".+")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class LlmGatewaySmokeTest {

    private final List<String> savedKeys = new ArrayList<>();

    @Test
    void aPromptReachesTheModelAndItsToolCallIsExecuted() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(System.getenv("PP_LLM_GATEWAY_URL"))
                        .apiKey(System.getenv().getOrDefault("PP_LLM_GATEWAY_KEY", "unset"))
                        .model(System.getenv().getOrDefault("PP_LLM_GATEWAY_MODEL", "pp-designer"))
                        .build())
                .build();

        String answer = ChatClient.create(model)
                .prompt("Save a draft entity definition with the key 'invoice' using the tool, then say done.")
                .tools(new DraftTools())
                .call()
                .content();

        System.out.println("model answer: " + answer);
        assertThat(savedKeys).as("the model called the tool").containsExactly("invoice");
    }

    private final class DraftTools {
        @Tool(description = "Save a DRAFT entity definition. It is never published.")
        String saveEntityDraft(@ToolParam(description = "The key of the entity definition") String key) {
            savedKeys.add(key);
            return "saved draft " + key;
        }
    }
}
