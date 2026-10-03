package com.processpuzzle.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.ai.adapter.outbound.HttpVisionServer;
import com.processpuzzle.ai.usecase.port.UnavailableVisionServer;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiConfigurationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void anUnconfiguredServerRefusesEveryOperation(String baseUrl) {
        AiProperties properties = new AiProperties();
        properties.getVisionServer().setBaseUrl(baseUrl);
        VisionServer server = new AiConfiguration().visionServer(properties);
        UUID jobId = UUID.randomUUID();
        var request = new VisionServer.EnrollmentRequest(jobId, "my-org", "token", "boat", false, null, List.of());

        assertThat(server).isInstanceOf(UnavailableVisionServer.class);
        assertThatThrownBy(() -> server.submitEnrollment(request))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("No vision server is configured");
        assertThatThrownBy(() -> server.job(jobId))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("No vision server is configured");
        assertThatThrownBy(() -> server.enrollmentResult(jobId))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("No vision server is configured");
        assertThatThrownBy(() -> server.discard(jobId))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("No vision server is configured");
    }

    @Test
    void aConfiguredServerBindsPropertiesAndCreatesTheHttpAdapter() {
        new ApplicationContextRunner().withUserConfiguration(AiConfiguration.class)
                .withPropertyValues(
                        "processpuzzle.ai.vision-server.base-url=http://vision-server:8000/v1",
                        "processpuzzle.ai.vision-server.callback-base-url=http://backend:8080/api/v1",
                        "processpuzzle.ai.vision-server.connect-timeout=PT2S",
                        "processpuzzle.ai.vision-server.read-timeout=PT10S")
                .run(context -> {
                    assertThat(context).hasSingleBean(VisionServer.class);
                    assertThat(context.getBean(VisionServer.class)).isInstanceOf(HttpVisionServer.class);
                    var settings = context.getBean(AiProperties.class).getVisionServer();
                    assertThat(settings.getBaseUrl()).isEqualTo("http://vision-server:8000/v1");
                    assertThat(settings.getCallbackBaseUrl()).isEqualTo("http://backend:8080/api/v1");
                    assertThat(settings.getConnectTimeout()).hasSeconds(2);
                    assertThat(settings.getReadTimeout()).hasSeconds(10);
                });
    }
}
