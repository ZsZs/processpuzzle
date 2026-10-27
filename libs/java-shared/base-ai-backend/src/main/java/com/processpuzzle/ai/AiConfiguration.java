package com.processpuzzle.ai;

import com.processpuzzle.ai.adapter.outbound.HttpVisionServer;
import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.DefinitionDrafts;
import com.processpuzzle.ai.assistant.DesignAssistant;
import com.processpuzzle.ai.assistant.SecurityContextAssistantAccess;
import com.processpuzzle.ai.usecase.port.UnavailableVisionServer;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.security.CurrentPrincipal;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The module's configuration. Scheduling is enabled here because the poller and the upload sweep are
 * this module's; {@code @EnableScheduling} is idempotent, so another module enabling it too is harmless.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    /**
     * The HTTP adapter when a vision server is configured, otherwise one that is always unavailable —
     * photos are then accepted and stay PENDING. A factory rather than {@code @ConditionalOnProperty},
     * because the property is declared as {@code ${VISION_SERVER_URL:}}, and an empty value counts as
     * present for that condition.
     */
    @Bean
    public VisionServer visionServer(AiProperties properties) {
        String baseUrl = properties.getVisionServer().getBaseUrl();
        return baseUrl == null || baseUrl.isBlank() ? new UnavailableVisionServer() : new HttpVisionServer(properties);
    }

    /**
     * The default authorization of the design assistant, from the request's security context. An application that
     * authorizes it another way declares its own {@link AssistantAccess} bean, and this one steps aside.
     */
    @Bean
    @ConditionalOnMissingBean(AssistantAccess.class)
    @ConditionalOnProperty(prefix = "processpuzzle.ai.assistant", name = "enabled", havingValue = "true")
    public AssistantAccess assistantAccess(CurrentPrincipal principal) {
        return new SecurityContextAssistantAccess(principal);
    }

    /**
     * The design assistant, built only when enabled: the gateway's chat model is then built here rather than by Spring
     * AI's auto-configuration, which would need a key even when the assistant is off. Authorization comes from the
     * application's {@link AssistantAccess} bean, or the default; the authoring participants come from the kinds it has.
     */
    @Bean
    @ConditionalOnProperty(prefix = "processpuzzle.ai.assistant", name = "enabled", havingValue = "true")
    public DesignAssistant designAssistant(AiProperties properties, AssistantAccess access,
                                           ObjectProvider<DefinitionAuthoringParticipant> participants) {
        AiProperties.Assistant assistant = properties.getAssistant();
        OpenAiChatModel model = OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .baseUrl(assistant.getBaseUrl())
                .apiKey(assistant.getApiKey())
                .model(assistant.getModel())
                .build())
            .build();
        return new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(participants.orderedStream().toList()), access, assistant.getMaxToolCalls());
    }
}
