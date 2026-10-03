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
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.MediaStore;
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
 * callback token is checked, a result is applied photo by photo, and the poller recovers a job the
 * server forgot.
 */
class VisionJobsTest {

    private static final String ORG = "my-org";

    private VisionJobTicketRepository tickets;
    private EnrollmentPhotoRepository photos;
    private MediaStore store;
    private VisionServer server;
    private VisionJobs jobs;
    private VisionJobTicket ticket;
    private EnrollmentPhoto photo;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tickets = mock(VisionJobTicketRepository.class);
        photos = mock(EnrollmentPhotoRepository.class);
        store = mock(MediaStore.class);
        server = mock(VisionServer.class);
        RecognitionProfiles profiles = mock(RecognitionProfiles.class);
        RecognitionProfile profile = new RecognitionProfile(ORG, "Boat");
        profile.setDetectorClass("boat");
        profile.setIdentifierAttributeKey("sailNumber");
        when(profiles.require(ORG, "Boat")).thenReturn(profile);
        ObjectProvider<MediaStore> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(store);
        when(store.internalReadUrl(anyString(), any())).thenAnswer(call -> "http://minio:9000/" + call.getArgument(0));

        ticket = VisionJobTicket.open(ORG, VisionJobTicket.Kind.ENROLLMENT, Instant.now());
        MediaUpload upload = new MediaUpload(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 1, Instant.now().plusSeconds(60));
        photo = new EnrollmentPhoto(upload, "Boat", UUID.randomUUID(), Instant.now());
        photo.assignTo(ticket.getJobId());
        when(tickets.findById(ticket.getJobId())).thenReturn(Optional.of(ticket));
        when(photos.findByVisionJobId(ticket.getJobId())).thenReturn(List.of(photo));

        jobs = new VisionJobs(tickets, photos, profiles, new MediaStores(provider), server,
                mock(PlatformTransactionManager.class), new AiProperties());
    }

    @Test
    void dispatchSubmitsInternalUrlsAndAFreshToken() {
        jobs.dispatch(ticket.getJobId());

        ArgumentCaptor<VisionServer.EnrollmentRequest> request = ArgumentCaptor.forClass(VisionServer.EnrollmentRequest.class);
        verify(server).submitEnrollment(request.capture());
        assertThat(request.getValue().photos()).singleElement().satisfies(media -> {
            assertThat(media.mediaId()).isEqualTo(photo.getPhotoId().toString());
            assertThat(media.url()).startsWith("http://minio:9000/");
        });
        assertThat(request.getValue().readIdentifier()).isTrue();
        assertThat(ticket.accepts(request.getValue().callbackToken())).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(VisionJobTicket.Status.SUBMITTED);
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
}
