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
import com.processpuzzle.ai.usecase.port.VisionJobRefusedException;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import com.processpuzzle.ai.vision.api.VisionApi;
import com.processpuzzle.ai.vision.model.CandidateScore;
import com.processpuzzle.ai.vision.model.Crop;
import com.processpuzzle.ai.vision.model.EnrollmentJobRequest;
import com.processpuzzle.ai.vision.model.EnrollmentJobResult;
import com.processpuzzle.ai.vision.model.EnrollmentPhotoResult;
import com.processpuzzle.ai.vision.model.IdentifierReading;
import com.processpuzzle.ai.vision.model.RecognitionJobRequest;
import com.processpuzzle.ai.vision.model.RecognitionJobResult;
import com.processpuzzle.ai.vision.model.TrackResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "///"})
    void callbackUrlsPreserveTheConfiguredApiRootWithoutDuplicateSlashes(String suffix) {
        AiProperties.VisionServer settings = new AiProperties.VisionServer();
        settings.setCallbackBaseUrl("http://testbed-backend:8080/api/v1" + suffix);

        assertThat(new HttpVisionServer(api, settings).callbackUrl("my-org"))
                .isEqualTo("http://testbed-backend:8080/api/v1/organizations/my-org/vision-notifications");
    }

    @Test
    void callbackUrlsTreatTheOrganizationKeyAsASinglePathSegment() {
        assertThat(server.callbackUrl("my org/other"))
                .isEqualTo("http://testbed-backend:8080/organizations/my%20org%2Fother/vision-notifications");
    }

    @Test
    void callbackUrlsHandleALongRunOfTrailingSlashes() {
        AiProperties.VisionServer settings = new AiProperties.VisionServer();
        settings.setCallbackBaseUrl("http://testbed-backend:8080/api/v1" + "/".repeat(100_000));

        assertThat(new HttpVisionServer(api, settings).callbackUrl("my-org"))
                .isEqualTo("http://testbed-backend:8080/api/v1/organizations/my-org/vision-notifications");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aMissingCallbackBaseUrlIsUnavailable(String baseUrl) {
        AiProperties.VisionServer settings = new AiProperties.VisionServer();
        settings.setCallbackBaseUrl(baseUrl);
        HttpVisionServer unconfigured = new HttpVisionServer(api, settings);

        assertThatThrownBy(() -> unconfigured.callbackUrl("my-org"))
                .isInstanceOf(VisionServerUnavailableException.class)
                .hasMessage("processpuzzle.ai.vision-server.callback-base-url is not set");
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

    @Test
    void aRecognitionCarriesFramesCandidatesGalleriesAndMatchingSettings() {
        when(api.submitRecognitionJob(any())).thenReturn(ResponseEntity.accepted().build());
        UUID jobId = UUID.randomUUID();

        server.submitRecognition(new VisionServer.RecognitionRequest(jobId, "my-org", "t", "boat", true, "^[A-Z]+$",
                0.6, 0.75, 0.1, List.of(new VisionServer.Media("f1", "http://minio:9000/f1")),
                List.of(new VisionServer.Candidate("c1", "GER 1", List.of(new VisionServer.GalleryEmbedding("dino", new byte[] {1, 2}))),
                        new VisionServer.Candidate("c2", null, List.of()))));

        ArgumentCaptor<RecognitionJobRequest> body = ArgumentCaptor.forClass(RecognitionJobRequest.class);
        verify(api).submitRecognitionJob(body.capture());
        RecognitionJobRequest sent = body.getValue();
        assertThat(sent.getJobId()).isEqualTo(jobId);
        assertThat(sent.getCallbackUrl()).hasToString("http://testbed-backend:8080/organizations/my-org/vision-notifications");
        assertThat(sent.getCallbackToken()).isEqualTo("t");
        assertThat(sent.getRequester().getStack()).isEqualTo("processpuzzle-testbed");
        assertThat(sent.getRequester().getOrgKey()).isEqualTo("my-org");
        assertThat(sent.getDetection().getDetectorClass()).isEqualTo("boat");
        assertThat(sent.getOcr().getIdentifierPattern()).isEqualTo("^[A-Z]+$");
        assertThat(sent.getMatching().getIdentifierWeight()).isEqualTo(0.6);
        assertThat(sent.getMatching().getAcceptScore()).isEqualTo(0.75);
        assertThat(sent.getMatching().getAcceptMargin()).isEqualTo(0.1);
        assertThat(sent.getFrames()).singleElement().satisfies(frame -> {
            assertThat(frame.getMediaId()).isEqualTo("f1");
            assertThat(frame.getUrl()).hasToString("http://minio:9000/f1");
        });
        assertThat(sent.getCandidates()).hasSize(2);
        assertThat(sent.getCandidates().getFirst().getCandidateId()).isEqualTo("c1");
        assertThat(sent.getCandidates().getFirst().getIdentifierText()).isEqualTo("GER 1");
        assertThat(sent.getCandidates().getFirst().getGallery()).singleElement().satisfies(embedding -> {
            assertThat(embedding.getModel()).isEqualTo("dino");
            assertThat(embedding.getVector()).containsExactly(1, 2);
        });
        assertThat(sent.getCandidates().getLast().getIdentifierText()).isNull();
        assertThat(sent.getCandidates().getLast().getGallery()).isEmpty();
    }

    @Test
    void aRecognitionWithoutIdentifierTurnsOcrOff() {
        when(api.submitRecognitionJob(any())).thenReturn(ResponseEntity.accepted().build());

        server.submitRecognition(new VisionServer.RecognitionRequest(UUID.randomUUID(), "my-org", "t", "boat", false, null,
                0.6, 0.75, 0.1, List.of(new VisionServer.Media("f1", "http://minio:9000/f1")), List.of()));

        ArgumentCaptor<RecognitionJobRequest> body = ArgumentCaptor.forClass(RecognitionJobRequest.class);
        verify(api).submitRecognitionJob(body.capture());
        assertThat(body.getValue().getOcr()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "CONFLICT"})
    void aRejectedSubmissionIsARefusalNotAnOutage(HttpStatus status) {
        when(api.submitRecognitionJob(any())).thenThrow(
                HttpClientErrorException.create(status, "no", null, "too many frames".getBytes(StandardCharsets.UTF_8), null));
        var request = new VisionServer.RecognitionRequest(UUID.randomUUID(), "my-org", "t", "boat", false, null,
                0.6, 0.75, 0.1, List.of(), List.of());

        assertThatThrownBy(() -> server.submitRecognition(request))
                .isInstanceOf(VisionJobRefusedException.class)
                .hasMessageContaining("too many frames");
    }

    @Test
    void anotherClientErrorIsUnavailable() {
        when(api.submitEnrollmentJob(any())).thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "no", null, null, null));
        var request = new VisionServer.EnrollmentRequest(UUID.randomUUID(), "my-org", "t", "boat", false, null, List.of());

        assertThatThrownBy(() -> server.submitEnrollment(request)).isInstanceOf(VisionServerUnavailableException.class);
    }

    @Test
    void anAutoMatchedTrackIsACertainSighting() {
        UUID jobId = UUID.randomUUID();
        RecognitionJobResult result = new RecognitionJobResult().jobId(jobId).tracks(List.of(
                new TrackResult().trackId(1).status(TrackResult.StatusEnum.AUTO_MATCHED).candidateId("c1").score(0.9)
                        .identifier(new IdentifierReading("GER 1", 0.8))
                        .bestCrop(new Crop().contentType("image/jpeg").data(new byte[] {5}).width(1).height(1))
                        .candidates(List.of(new CandidateScore().candidateId("c1").score(0.9).identifierScore(1.0).embeddingScore(0.7),
                                new CandidateScore().candidateId("c2").score(null))),
                new TrackResult().trackId(2).status(TrackResult.StatusEnum.NEEDS_REVIEW)));
        when(api.getRecognitionJobResult(jobId)).thenReturn(ResponseEntity.ok(result));

        VisionServer.SubjectSighting sighting = server.recognitionResult(jobId).subject().orElseThrow();

        assertThat(sighting).isEqualTo(new VisionServer.SubjectSighting("c1", 0.9, "GER 1", 0.8, new byte[] {5}, List.of(
                new VisionServer.CandidateScore("c1", 0.9, 1.0, 0.7),
                new VisionServer.CandidateScore("c2", 0, null, null))));
    }

    @Test
    void aTrackNeedingReviewNamesNoCandidate() {
        UUID jobId = UUID.randomUUID();
        when(api.getRecognitionJobResult(jobId)).thenReturn(ResponseEntity.ok(new RecognitionJobResult().jobId(jobId).tracks(List.of(
                new TrackResult().trackId(1).status(TrackResult.StatusEnum.NEEDS_REVIEW).candidateId("c1").score(0.5)))));

        VisionServer.SubjectSighting sighting = server.recognitionResult(jobId).subject().orElseThrow();

        assertThat(sighting.candidateId()).isNull();
        assertThat(sighting.score()).isEqualTo(0.5);
        assertThat(sighting.identifierText()).isNull();
        assertThat(sighting.cropJpeg()).isNull();
        assertThat(sighting.ranking()).isEmpty();
    }

    @Test
    void noTrackMeansNoSubject() {
        UUID jobId = UUID.randomUUID();
        when(api.getRecognitionJobResult(jobId)).thenReturn(ResponseEntity.ok(new RecognitionJobResult().jobId(jobId).tracks(List.of())));

        assertThat(server.recognitionResult(jobId).subject()).isEmpty();
    }

    @Test
    void aMissingOrEmptyRecognitionResultIsUnavailable() {
        UUID gone = UUID.randomUUID();
        when(api.getRecognitionJobResult(gone)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", null, null, null));
        assertThatThrownBy(() -> server.recognitionResult(gone))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("is gone");

        UUID empty = UUID.randomUUID();
        when(api.getRecognitionJobResult(empty)).thenReturn(ResponseEntity.ok(new RecognitionJobResult().jobId(empty).tracks(null)));
        assertThatThrownBy(() -> server.recognitionResult(empty))
                .isInstanceOf(VisionServerUnavailableException.class).hasMessageContaining("no result");
    }
}
