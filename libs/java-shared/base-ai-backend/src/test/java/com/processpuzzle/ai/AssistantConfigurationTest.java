package com.processpuzzle.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.DesignAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AssistantConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AiConfiguration.class)
        .withBean(AssistantAccess.class, () -> new AssistantAccess() {
            @Override
            public String currentOrganization() {
                return "acme";
            }

            @Override
            public boolean mayDesign() {
                return true;
            }
        });

    @Test
    void theAssistantIsNotBuiltWhileItIsDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DesignAssistant.class));
    }

    @Test
    void theAssistantIsBuiltWhenEnabledWithAGateway() {
        runner.withPropertyValues(
                "processpuzzle.ai.assistant.enabled=true",
                "processpuzzle.ai.assistant.base-url=http://localhost:4000/v1",
                "processpuzzle.ai.assistant.api-key=test-key")
            .run(context -> assertThat(context).hasSingleBean(DesignAssistant.class));
    }
}
