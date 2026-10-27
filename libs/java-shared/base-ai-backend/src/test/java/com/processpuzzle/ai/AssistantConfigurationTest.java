package com.processpuzzle.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.DesignAssistant;
import com.processpuzzle.ai.domain.AssistantSessionRepository;
import com.processpuzzle.ai.assistant.SecurityContextAssistantAccess;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AssistantConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AiConfiguration.class)
        .withBean(AssistantSessionRepository.class, () -> mock(AssistantSessionRepository.class))
        .withBean(AssistantAccess.class, () -> new AssistantAccess() {
            @Override
            public String currentOrganization() {
                return "acme";
            }

            @Override
            public boolean mayDesign() {
                return true;
            }

            @Override
            public String principal() {
                return "Ada";
            }

            @Override
            public String subject() {
                return "ada-subject";
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

    @Test
    void theDefaultAccessIsUsedWhenTheApplicationSuppliesNone() {
        new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class)
            .withBean(CurrentPrincipal.class, () -> new CurrentPrincipal(new SecurityProperties()))
            .withBean(AssistantSessionRepository.class, () -> mock(AssistantSessionRepository.class))
            .withPropertyValues("processpuzzle.ai.assistant.enabled=true",
                "processpuzzle.ai.assistant.base-url=http://localhost:4000/v1",
                "processpuzzle.ai.assistant.api-key=test-key")
            .run(context -> assertThat(context).hasSingleBean(AssistantAccess.class)
                .getBean(AssistantAccess.class).isInstanceOf(SecurityContextAssistantAccess.class));
    }
}
