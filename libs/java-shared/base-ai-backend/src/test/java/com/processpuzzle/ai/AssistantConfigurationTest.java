package com.processpuzzle.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.processpuzzle.ai.assist.AuthoringAssist;
import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.DesignAssistant;
import com.processpuzzle.ai.domain.AssistantSessionRepository;
import com.processpuzzle.ai.domain.OrganizationAiSubscriptionRepository;
import com.processpuzzle.ai.assistant.SecurityContextAssistantAccess;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AssistantConfigurationTest {

    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AiConfiguration.class)
        .withBean(AssistantSessionRepository.class, () -> mock(AssistantSessionRepository.class))
        .withBean(OrganizationAiSubscriptionRepository.class, () -> mock(OrganizationAiSubscriptionRepository.class))
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
        runner.run(context -> assertThat(context).doesNotHaveBean(DesignAssistant.class)
            .doesNotHaveBean(AuthoringAssist.class));
    }

    @Test
    void theAuthoringAssistIsBuiltWithTheAssistantAndTakesTheFeaturesParticipants() {
        runner.withPropertyValues(
                "processpuzzle.ai.assistant.enabled=true",
                "processpuzzle.ai.assistant.secret-key=" + SECRET)
            .run(context -> assertThat(context).hasSingleBean(AuthoringAssist.class));
    }

    @Test
    void theAssistantIsBuiltWhenEnabledWithAMasterKey() {
        runner.withPropertyValues(
                "processpuzzle.ai.assistant.enabled=true",
                "processpuzzle.ai.assistant.secret-key=" + SECRET)
            .run(context -> assertThat(context).hasSingleBean(DesignAssistant.class));
    }

    @Test
    void enablingTheAssistantWithoutAMasterKeyFailsStartup() {
        runner.withPropertyValues("processpuzzle.ai.assistant.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theDefaultAccessIsUsedWhenTheApplicationSuppliesNone() {
        new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class)
            .withBean(CurrentPrincipal.class, () -> new CurrentPrincipal(new SecurityProperties()))
            .withBean(AssistantSessionRepository.class, () -> mock(AssistantSessionRepository.class))
        .withBean(OrganizationAiSubscriptionRepository.class, () -> mock(OrganizationAiSubscriptionRepository.class))
            .withPropertyValues("processpuzzle.ai.assistant.enabled=true",
                "processpuzzle.ai.assistant.secret-key=" + SECRET)
            .run(context -> assertThat(context).hasSingleBean(AssistantAccess.class)
                .getBean(AssistantAccess.class).isInstanceOf(SecurityContextAssistantAccess.class));
    }
}
