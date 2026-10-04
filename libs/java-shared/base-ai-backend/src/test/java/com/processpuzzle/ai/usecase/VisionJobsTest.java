package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.domain.Recognition;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.ai.usecase.port.VisionJobRefusedException;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The vision job life cycle against mocks. What matters: a server that is down never loses work, the
 * callback token is checked, a result is applied photo by photo or to its recognition, and the poller
 * recovers a job the server forgot.
 */
class VisionJobsTest {

    private static final String ORG = "my-org";

    private VisionJobTicketRepository tickets;
    private EnrollmentPhotoRepository photos;
    private RecognitionRepository recognitions;
    private MediaUploadRepository uploads;
    private MediaStore store;
    private SubjectDirectory subjects;
    private VisionServer server;
    private RecognitionProfile profile;
    private VisionJobs jobs;
    private VisionJobTicket ticket;
    private EnrollmentPhoto photo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tickets = mock(VisionJobTicketRepository.class);
        photos = mock(EnrollmentPhotoRepository.class);
        recognitions = mock(RecognitionRepository.class);
        uploads = mock(MediaUploadRepository.class);
        store = mock(MediaStore.class);
        subjects = mock(SubjectDirectory.class);
        server = mock(VisionServer.class);
        RecognitionProfiles profiles = mock(RecognitionProfiles.class);
        profile = new RecognitionProfile(ORG, "Boat");
        profile.setDetectorClass("boat");
        profile.setIdentifierAttributeKey("sailNumber");
        profile.setIdentifierPattern("^[A-Z]{3} ?[0-9]+$");
        when(profiles.require(ORG, "Boat")).thenReturn(profile);
        ObjectProvider<MediaStore> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(store);
        when(store.internalReadUrl(anyString(), any())).thenAnswer(call -> "http://minio:9000/" + call.getArgument(0));
        when(subjects.internalPhotoUrl(anyString(), any())).thenAnswer(call -> "http://artifacts:9000/" + call.getArgument(0));

        ticket = VisionJobTicket.open(ORG, VisionJobTicket.Kind.ENROLLMENT, Instant.now());
        photo = new EnrollmentPhoto(ORG, "Boat", UUID.randomUUID(), "artifact-1", Instant.now());
        photo.assignTo(ticket.getJobId());
        when(tickets.findById(ticket.getJobId())).thenReturn(Optional.of(ticket));
        when(photos.findByVisionJobId(ticket.getJobId())).thenReturn(List.of(photo));

        jobs = new VisionJobs(tickets, photos, recognitions, uploads, profiles, new MediaStores(provider), subjects, server,
                mock(PlatformTransactionManager.class), new AiProperties(), Runnable::run);
    }

    // ── Enrollment ─────────────────────────────────────────────────

    @Test
    void dispatchSubmitsInternalPhotoUrlsAndAFreshToken() {
        jobs.dispatch(ticket.getJobId());

        ArgumentCaptor<VisionServer.EnrollmentRequest> request = ArgumentCaptor.forClass(VisionServer.EnrollmentRequest.class);
        verify(server).submitEnrollment(request.capture());
        assertThat(request.getValue().photos()).singleElement().satisfies(media -> {
            assertThat(media.mediaId()).isEqualTo(photo.getPhotoId().toString());
            assertThat(media.url()).isEqualTo("http://artifacts:9000/artifact-1");
        });
        assertThat(request.getValue().readIdentifier()).isTrue();
        assertThat(ticket.accepts(request.getValue().callbackToken())).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.SUBMITTED);
        verify(subjects).internalPhotoUrl("artifact-1", new AiProperties().getMedia().getVisionUrlExpiry());
    }

    @Test
    void aPhotoThatCannotBeReadFailsAndAnEmptyBatchIsNotSubmitted() {
        when(subjects.internalPhotoUrl(anyString(), any())).thenThrow(new IllegalStateException("artifact gone"));

        jobs.dispatch(ticket.getJobId());

        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.FAILED);
        assertThat(photo.getFailureReason()).contains("artifact gone");
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
        verifyNoInteractions(server);
    }

    @Test
    void dispatchLaterRunsTheDispatchOnTheBackgroundExecutorAndIgnoresNull() {
        jobs.dispatchLater(null);
        verifyNoInteractions(server);

        jobs.dispatchLater(ticket.getJobId());

        verify(server).submitEnrollment(any());
    }

    @Test
    void dispatchIgnoresMissingAndCompletedTickets() {
        jobs.dispatch(UUID.randomUUID());
        ticket.completed(Instant.now());

        jobs.dispatch(ticket.getJobId());

        verifyNoInteractions(server, store, photos);
    }

    @Test
    void dispatchCompletesATicketWithNoPhotosWithoutSubmitting() {
        when(photos.findByVisionJobId(ticket.getJobId())).thenReturn(List.of());

        jobs.dispatch(ticket.getJobId());

        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
        verifyNoInteractions(server, store);
    }

    @Test
    void aNotificationForAnUnknownTicketIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> jobs.notified(ORG, "token", unknown, VisionServer.Status.DONE))
                .isInstanceOfSatisfying(AiRequestException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.NOT_FOUND);
                    assertThat(e.getErrorId()).isEqualTo("ai.vision-job.not-found");
                });
        verifyNoInteractions(server, store, photos);
    }

    @Test
    void anUnreachableServerLeavesTheTicketForThePoller() {
        doThrow(new VisionServerUnavailableException("down")).when(server).submitEnrollment(any());

        jobs.dispatch(ticket.getJobId());

        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.SUBMITTING);
        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.PENDING);
    }

    @Test
    void aRefusedEnrollmentFailsItsPhotosAtOnce() {
        doThrow(new VisionJobRefusedException("bad request", null)).when(server).submitEnrollment(any());

        jobs.dispatch(ticket.getJobId());

        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
        assertThat(ticket.getFailureReason()).isEqualTo("bad request");
        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.FAILED);
    }

    @Test
    void aNotificationWithTheWrongTokenIsRefused() {
        ticket.newCallbackToken();

        assertThatThrownBy(() -> jobs.notified(ORG, "forged", ticket.getJobId(), VisionServer.Status.DONE))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.UNAUTHORIZED));
        assertThatThrownBy(() -> jobs.notified("other-org", "forged", ticket.getJobId(), VisionServer.Status.DONE))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.NOT_FOUND));
        verify(server, never()).enrollmentResult(any());
    }

    @Test
    void aDoneNotificationFetchesAndAppliesTheResult() {
        String token = ticket.newCallbackToken();
        byte[] crop = {1, 2, 3};
        byte[] vector = new byte[16];
        when(server.enrollmentResult(ticket.getJobId())).thenReturn(new VisionServer.EnrollmentOutcome(List.of(
                new VisionServer.PhotoOutcome(photo.getPhotoId().toString(), EnrollmentPhotoStatus.ENROLLED, crop,
                        "facebook/dinov2-small", vector, "GER 1234", null))));

        jobs.notified(ORG, token, ticket.getJobId(), VisionServer.Status.DONE);

        String cropName = ORG + "/crops/" + photo.getPhotoId() + ".jpg";
        verify(store).put(cropName, crop, "image/jpeg");
        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.ENROLLED);
        assertThat(photo.getObservedIdentifier()).isEqualTo("GER 1234");
        assertThat(photo.getEmbeddingModel()).isEqualTo("facebook/dinov2-small");
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
        verify(server).discard(ticket.getJobId());
    }

    @Test
    void aFailedJobFailsItsPhotosWithTheServersReason() {
        String token = ticket.newCallbackToken();
        when(server.job(ticket.getJobId())).thenReturn(Optional.of(
                new VisionServer.JobState(VisionServer.Status.FAILED, "media could not be fetched")));

        jobs.notified(ORG, token, ticket.getJobId(), VisionServer.Status.FAILED);

        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.FAILED);
        assertThat(photo.getFailureReason()).isEqualTo("media could not be fetched");
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
    }

    @Test
    void thePollerResubmitsAJobTheServerForgot() {
        ticket.submitted(Instant.now().minusSeconds(3600));
        when(tickets.findByStatusInAndTouchedAtBefore(any(), any())).thenReturn(List.of(ticket));
        when(server.job(ticket.getJobId())).thenReturn(Optional.empty());

        jobs.poll();

        verify(server).submitEnrollment(any());
        assertThat(ticket.getSubmitAttempts()).isEqualTo(1);
    }

    @Test
    void aTicketThatNeverReachesTheServerIsEventuallyFailed() {
        AiProperties properties = new AiProperties();
        for (int i = 0; i < properties.getPoller().getMaxSubmitAttempts(); i++) {
            ticket.submitting(Instant.now());
        }

        jobs.dispatch(ticket.getJobId());

        verify(server, never()).submitEnrollment(any());
        assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.FAILED);
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
        verify(store, never()).put(anyString(), any(), eq("image/jpeg"));
    }

    // ── Recognition ────────────────────────────────────────────────

    private record RecognitionFixture(VisionJobTicket ticket, Recognition recognition, MediaUpload frame,
                                      UUID withGallery, UUID withoutGallery) {
    }

    private RecognitionFixture recognition() {
        VisionJobTicket recognitionTicket = VisionJobTicket.open(ORG, VisionJobTicket.Kind.RECOGNITION, Instant.now());
        MediaUpload frame = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now().plusSeconds(60));
        UUID withGallery = UUID.randomUUID();
        UUID withoutGallery = UUID.randomUUID();
        Recognition recognition = new Recognition(ORG, "Boat", List.of(frame.getMediaKey()), List.of(withGallery, withoutGallery),
                recognitionTicket.getJobId(), Instant.now());
        when(tickets.findById(recognitionTicket.getJobId())).thenReturn(Optional.of(recognitionTicket));
        when(recognitions.findByVisionJobId(recognitionTicket.getJobId())).thenReturn(Optional.of(recognition));
        when(uploads.findAllById(List.of(frame.getMediaKey()))).thenReturn(List.of(frame));
        return new RecognitionFixture(recognitionTicket, recognition, frame, withGallery, withoutGallery);
    }

    @Test
    void aRecognitionIsSubmittedWithItsFramesAndEveryCandidatesGalleryAndIdentifier() {
        RecognitionFixture fixture = recognition();
        profile.setMatching(new MatchingSettings(0.7, 0.8, 0.2, 3));
        EnrollmentPhoto enrolled = new EnrollmentPhoto(ORG, "Boat", fixture.withGallery(), "artifact-9", Instant.now());
        enrolled.enrolled("crop", "dinov2-small", new byte[] {4, 2}, null);
        when(photos.findByOrgKeyAndEntityNameAndObjectIdInAndStatus(ORG, "Boat",
                List.of(fixture.withGallery(), fixture.withoutGallery()), EnrollmentPhotoStatus.ENROLLED))
                .thenReturn(List.of(enrolled));
        when(subjects.identifier(ORG, "Boat", fixture.withGallery(), "sailNumber")).thenReturn(Optional.of("GER 1"));
        when(subjects.identifier(ORG, "Boat", fixture.withoutGallery(), "sailNumber")).thenReturn(Optional.empty());

        jobs.dispatch(fixture.ticket().getJobId());

        ArgumentCaptor<VisionServer.RecognitionRequest> request = ArgumentCaptor.forClass(VisionServer.RecognitionRequest.class);
        verify(server).submitRecognition(request.capture());
        VisionServer.RecognitionRequest sent = request.getValue();
        assertThat(sent.jobId()).isEqualTo(fixture.ticket().getJobId());
        assertThat(sent.detectorClass()).isEqualTo("boat");
        assertThat(sent.readIdentifier()).isTrue();
        assertThat(sent.identifierPattern()).isEqualTo("^[A-Z]{3} ?[0-9]+$");
        assertThat(sent.identifierWeight()).isEqualTo(0.7);
        assertThat(sent.acceptScore()).isEqualTo(0.8);
        assertThat(sent.acceptMargin()).isEqualTo(0.2);
        assertThat(sent.frames()).containsExactly(new VisionServer.Media(fixture.frame().getMediaKey().toString(),
                "http://minio:9000/" + fixture.frame().getObjectName()));
        assertThat(sent.candidates()).containsExactly(
                new VisionServer.Candidate(fixture.withGallery().toString(), "GER 1",
                        List.of(new VisionServer.GalleryEmbedding("dinov2-small", new byte[] {4, 2}))),
                new VisionServer.Candidate(fixture.withoutGallery().toString(), null, List.of()));
        assertThat(fixture.ticket().accepts(sent.callbackToken())).isTrue();
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.SUBMITTED);
        assertThat(fixture.recognition().getCandidatesWithoutGallery()).containsExactly(fixture.withoutGallery());
    }

    @Test
    void aProfileWithoutIdentifierSendsNoIdentifiersAndDefaultMatching() {
        RecognitionFixture fixture = recognition();
        profile.setIdentifierAttributeKey(null);
        profile.setMatching(null);

        jobs.dispatch(fixture.ticket().getJobId());

        ArgumentCaptor<VisionServer.RecognitionRequest> request = ArgumentCaptor.forClass(VisionServer.RecognitionRequest.class);
        verify(server).submitRecognition(request.capture());
        assertThat(request.getValue().readIdentifier()).isFalse();
        assertThat(request.getValue().acceptScore()).isEqualTo(MatchingSettings.DEFAULT_ACCEPT_SCORE);
        assertThat(request.getValue().candidates()).extracting(VisionServer.Candidate::identifierText).containsOnlyNulls();
        verify(subjects, never()).identifier(anyString(), anyString(), any(), any());
        assertThat(fixture.recognition().getCandidatesWithoutGallery())
                .containsExactly(fixture.withGallery(), fixture.withoutGallery());
    }

    @Test
    void aRecognitionWhoseFramesAreGoneFails() {
        RecognitionFixture fixture = recognition();
        when(uploads.findAllById(any())).thenReturn(List.of());

        jobs.dispatch(fixture.ticket().getJobId());

        verifyNoInteractions(server);
        assertThat(fixture.recognition().getStatus()).isEqualTo(Recognition.Status.FAILED);
        assertThat(fixture.recognition().getFailureReason()).isEqualTo("the frames are gone");
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
    }

    @Test
    void aTicketWhoseRecognitionIsGoneIsCompletedWithoutSubmitting() {
        RecognitionFixture fixture = recognition();
        when(recognitions.findByVisionJobId(fixture.ticket().getJobId())).thenReturn(Optional.empty());

        jobs.dispatch(fixture.ticket().getJobId());

        verifyNoInteractions(server);
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
    }

    @Test
    void aRefusedRecognitionFailsAtOnce() {
        RecognitionFixture fixture = recognition();
        doThrow(new VisionJobRefusedException("too many frames", null)).when(server).submitRecognition(any());

        jobs.dispatch(fixture.ticket().getJobId());

        assertThat(fixture.recognition().getStatus()).isEqualTo(Recognition.Status.FAILED);
        assertThat(fixture.recognition().getFailureReason()).isEqualTo("too many frames");
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
    }

    @Test
    void aCertainMatchIsStoredWithItsCropAndRanking() {
        RecognitionFixture fixture = recognition();
        String token = fixture.ticket().newCallbackToken();
        byte[] crop = {7, 7};
        when(server.recognitionResult(fixture.ticket().getJobId())).thenReturn(new VisionServer.RecognitionOutcome(Optional.of(
                new VisionServer.SubjectSighting(fixture.withGallery().toString(), 0.91, "GER 1", 0.88, crop, List.of(
                        new VisionServer.CandidateScore(fixture.withGallery().toString(), 0.91, 1.0, 0.8),
                        new VisionServer.CandidateScore(fixture.withoutGallery().toString(), 0.3, null, null))))));

        jobs.notified(ORG, token, fixture.ticket().getJobId(), VisionServer.Status.DONE);

        Recognition recognition = fixture.recognition();
        String cropName = ORG + "/recognitions/" + recognition.getRecognitionId() + ".jpg";
        verify(store).put(cropName, crop, "image/jpeg");
        assertThat(recognition.getStatus()).isEqualTo(Recognition.Status.DONE);
        assertThat(recognition.getOutcome()).isEqualTo(Recognition.Outcome.MATCHED);
        assertThat(recognition.getObjectId()).isEqualTo(fixture.withGallery());
        assertThat(recognition.getScore()).isEqualTo(0.91);
        assertThat(recognition.getObservedIdentifier()).isEqualTo("GER 1");
        assertThat(recognition.getObservedIdentifierConfidence()).isEqualTo(0.88);
        assertThat(recognition.getCropObjectName()).isEqualTo(cropName);
        assertThat(recognition.getRanking()).extracting(Recognition.RankedCandidate::getObjectId)
                .containsExactly(fixture.withGallery(), fixture.withoutGallery());
        assertThat(recognition.getRanking().getFirst().getIdentifierScore()).isEqualTo(1.0);
        assertThat(recognition.getFinishedAt()).isNotNull();
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
        verify(server).discard(fixture.ticket().getJobId());
    }

    @Test
    void anUncertainSightingNeedsReviewAndKeepsNoScore() {
        RecognitionFixture fixture = recognition();
        String token = fixture.ticket().newCallbackToken();
        when(server.recognitionResult(fixture.ticket().getJobId())).thenReturn(new VisionServer.RecognitionOutcome(Optional.of(
                new VisionServer.SubjectSighting(null, 0.6, null, null, null, List.of(
                        new VisionServer.CandidateScore(fixture.withGallery().toString(), 0.6, null, 0.6))))));

        jobs.notified(ORG, token, fixture.ticket().getJobId(), VisionServer.Status.DONE);

        Recognition recognition = fixture.recognition();
        assertThat(recognition.getOutcome()).isEqualTo(Recognition.Outcome.NEEDS_REVIEW);
        assertThat(recognition.getObjectId()).isNull();
        assertThat(recognition.getScore()).isNull();
        assertThat(recognition.getCropObjectName()).isNull();
        assertThat(recognition.getRanking()).hasSize(1);
        verify(store, never()).put(anyString(), any(), anyString());
    }

    @Test
    void framesWithoutASubjectAreNoSubject() {
        RecognitionFixture fixture = recognition();
        String token = fixture.ticket().newCallbackToken();
        when(server.recognitionResult(fixture.ticket().getJobId())).thenReturn(new VisionServer.RecognitionOutcome(Optional.empty()));

        jobs.notified(ORG, token, fixture.ticket().getJobId(), VisionServer.Status.DONE);

        assertThat(fixture.recognition().getStatus()).isEqualTo(Recognition.Status.DONE);
        assertThat(fixture.recognition().getOutcome()).isEqualTo(Recognition.Outcome.NO_SUBJECT);
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.COMPLETED);
        verifyNoInteractions(store);
    }

    @Test
    void aFailedRecognitionJobFailsTheRecognition() {
        RecognitionFixture fixture = recognition();
        String token = fixture.ticket().newCallbackToken();
        when(server.job(fixture.ticket().getJobId())).thenReturn(Optional.empty());

        jobs.notified(ORG, token, fixture.ticket().getJobId(), VisionServer.Status.CANCELLED);

        assertThat(fixture.recognition().getStatus()).isEqualTo(Recognition.Status.FAILED);
        assertThat(fixture.recognition().getFailureReason()).isEqualTo("the vision job was cancelled");
        assertThat(fixture.ticket().getStatus()).isEqualTo(VisionJobTicket.Status.FAILED);
        verify(server, never()).recognitionResult(any());
    }
}
