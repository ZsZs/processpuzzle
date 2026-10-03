package com.processpuzzle.ai;

import com.processpuzzle.ai.adapter.outbound.HttpVisionServer;
import com.processpuzzle.ai.usecase.port.UnavailableVisionServer;
import com.processpuzzle.ai.usecase.port.VisionServer;
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
}
