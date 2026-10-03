package com.processpuzzle.ai.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import com.processpuzzle.ai.vision.api.VisionApi;
import com.processpuzzle.ai.vision.model.Crop;
import com.processpuzzle.ai.vision.model.EnrollmentJobRequest;
import com.processpuzzle.ai.vision.model.EnrollmentJobResult;
import com.processpuzzle.ai.vision.model.EnrollmentPhotoResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class HttpVisionServerTest {

    private VisionApi api;
    private HttpVisionServer server;

    @BeforeEach
    void setUp() {
        api = mock(VisionApi.class);
        AiProperties.VisionServer settings = new AiProperties.VisionServer();
        settings.setCallbackBaseUrl("http://testbed-backend:8080/");
        settings.setStack("processpuzzle-testbed");
        server = new HttpVisionServer(api, settings);
    }

    @Test
    void anEnrollmentCarriesTheCallbackUrlTheStackAndNoOcrWithoutAnIdentifier() {
        when(api.submitEnrollmentJob(any())).thenReturn(ResponseEntity.accepted().build());

        server.submitEnrollment(new VisionServer.EnrollmentRequest(UUID.randomUUID(), "my-org", "t", "boat", false, null,
                List.of(new VisionServer.Media("p1", "http://minio:9000/a"))));

        ArgumentCaptor<EnrollmentJobRequest> body = ArgumentCaptor.forClass(EnrollmentJobRequest.class);
        verify(api).submitEnrollmentJob(body.capture());
        assertThat(body.getValue().getCallbackUrl())
                .hasToString("http://testbed-backend:8080/organizations/my-org/vision-notifications");
        assertThat(body.getValue().getRequester().getStack()).isEqualTo("processpuzzle-testbed");
        assertThat(body.getValue().getOcr()).isNull();
    }

    @Test
    void anUnknownJobIsEmptyAndAnUnreachableServerIsUnavailable() {
        UUID jobId = UUID.randomUUID();
        when(api.getVisionJob(jobId)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", null, null, null));
        assertThat(server.job(jobId)).isEmpty();

        when(api.deleteVisionJob(jobId)).thenThrow(new ResourceAccessException("connection refused"));
        assertThatThrownBy(() -> server.discard(jobId)).isInstanceOf(VisionServerUnavailableException.class);
    }

    @Test
    void anEnrollmentResultMapsPhotoByPhoto() {
        UUID jobId = UUID.randomUUID();
        EnrollmentJobResult result = new EnrollmentJobResult().jobId(jobId).photos(List.of(
                new EnrollmentPhotoResult().mediaId("p1").status(EnrollmentPhotoResult.StatusEnum.ENROLLED)
                        .crop(new Crop().contentType("image/jpeg").data(new byte[] {9}).width(1).height(1)),
                new EnrollmentPhotoResult().mediaId("p2").status(EnrollmentPhotoResult.StatusEnum.NO_SUBJECT)));
        when(api.getEnrollmentJobResult(jobId)).thenReturn(ResponseEntity.ok(result));

        List<VisionServer.PhotoOutcome> photos = server.enrollmentResult(jobId).photos();

        assertThat(photos).extracting(VisionServer.PhotoOutcome::status)
                .containsExactly(EnrollmentPhotoStatus.ENROLLED, EnrollmentPhotoStatus.NO_SUBJECT);
        assertThat(photos.getFirst().cropJpeg()).containsExactly(9);
    }
}
