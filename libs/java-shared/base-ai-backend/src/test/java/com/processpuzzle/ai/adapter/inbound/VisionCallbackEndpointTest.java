package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.ai.model.VisionJobNotification;
import com.processpuzzle.ai.usecase.VisionJobs;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.VisionServer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class VisionCallbackEndpointTest {

    private final VisionJobs jobs = mock(VisionJobs.class);
    private final VisionCallbackEndpoint endpoint = new VisionCallbackEndpoint(jobs);

    @ParameterizedTest
    @EnumSource(VisionJobNotification.StatusEnum.class)
    void callbacksForwardTheTenantTokenJobAndTerminalStatus(VisionJobNotification.StatusEnum status) {
        UUID jobId = UUID.randomUUID();
        var notification = new VisionJobNotification(jobId, VisionJobNotification.KindEnum.ENROLLMENT, status);

        var response = endpoint.receiveVisionJobNotification("my-org", "callback-token", notification);

        verify(jobs).notified("my-org", "callback-token", jobId, VisionServer.Status.valueOf(status.getValue()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void rejectedTokensAreNotAcknowledgedAsSuccessfulCallbacks() {
        UUID jobId = UUID.randomUUID();
        var refusal = new AiRequestException(AiRequestException.Kind.UNAUTHORIZED, "ai.vision-job.token-rejected", "wrong token");
        doThrow(refusal).when(jobs).notified("my-org", "wrong", jobId, VisionServer.Status.DONE);
        var notification = new VisionJobNotification(jobId, VisionJobNotification.KindEnum.ENROLLMENT,
                VisionJobNotification.StatusEnum.DONE);

        assertThatThrownBy(() -> endpoint.receiveVisionJobNotification("my-org", "wrong", notification)).isSameAs(refusal);
    }
}
