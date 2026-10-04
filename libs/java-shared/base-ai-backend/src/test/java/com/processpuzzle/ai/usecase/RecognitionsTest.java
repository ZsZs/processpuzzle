package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.AiProperties;
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
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class RecognitionsTest {

    private static final String ORG = "my-org";
    private static final UUID CANDIDATE = UUID.randomUUID();

    private RecognitionProfiles profiles;
    private RecognitionRepository repository;
    private VisionJobTicketRepository tickets;
    private MediaUploadRepository uploadRepository;
    private MediaUploads uploads;
    private Enrollments enrollments;
    private VisionJobs jobs;
    private MediaStore store;
    private SubjectDirectory subjects;
    private OrganizationGuard guard;
    private PlatformTransactionManager transactions;
    private AiProperties properties;
    private RecognitionProfile profile;
    private Recognitions recognitions;

    @BeforeEach
    void setUp() {
        profiles = mock(RecognitionProfiles.class);
        repository = mock(RecognitionRepository.class);
        tickets = mock(VisionJobTicketRepository.class);
        uploadRepository = mock(MediaUploadRepository.class);
        uploads = mock(MediaUploads.class);
        enrollments = mock(Enrollments.class);
        jobs = mock(VisionJobs.class);
        store = mock(MediaStore.class);
        MediaStores stores = mock(MediaStores.class);
        when(stores.get()).thenReturn(store);
        subjects = mock(SubjectDirectory.class);
        guard = mock(OrganizationGuard.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        properties = new AiProperties();
        profile = new RecognitionProfile(ORG, "Boat");
        when(profiles.require(ORG, "Boat")).thenReturn(profile);
        when(subjects.subjectExists(any(), any(), any())).thenReturn(true);
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));
        List<Recognition> stored = new java.util.ArrayList<>();
        when(repository.save(any())).thenAnswer(call -> {
            stored.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(repository.findByOrgKeyAndRecognitionId(any(), any())).thenAnswer(call -> stored.stream()
                .filter(recognition -> recognition.getOrgKey().equals(call.getArgument(0))
                        && recognition.getRecognitionId().equals(call.getArgument(1)))
                .findFirst());
        when(store.readUrl(anyString(), any())).thenAnswer(call -> "http://media/" + call.getArgument(0));
        var provider = new StaticListableBeanFactory(Map.of("subjects", subjects)).getBeanProvider(SubjectDirectory.class);
        recognitions = new Recognitions(profiles, repository, tickets, uploadRepository, uploads, enrollments, jobs, stores,
                provider, guard, transactions, properties);
    }

    private MediaUpload frame() {
        MediaUpload frame = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now().plusSeconds(60));
        when(uploads.claim(ORG, frame.getMediaKey().toString(), MediaPurpose.RECOGNITION_FRAME)).thenReturn(frame);
        return frame;
    }

    @Test
    void startingClaimsTheFramesOpensATicketAndDispatchesAfterCommitting() {
        MediaUpload first = frame();
        MediaUpload second = frame();
        String firstKey = first.getMediaKey().toString();

        RecognitionView view = recognitions.start(ORG, "Boat",
                List.of(firstKey, second.getMediaKey().toString(), firstKey), List.of(CANDIDATE, CANDIDATE));

        verify(guard, atLeastOnce()).requireAccess(ORG);
        verify(uploads).claim(ORG, firstKey, MediaPurpose.RECOGNITION_FRAME);
        ArgumentCaptor<VisionJobTicket> ticket = ArgumentCaptor.forClass(VisionJobTicket.class);
        verify(tickets).save(ticket.capture());
        assertThat(ticket.getValue().getKind()).isEqualTo(VisionJobTicket.Kind.RECOGNITION);
        ArgumentCaptor<Recognition> saved = ArgumentCaptor.forClass(Recognition.class);
        verify(repository).save(saved.capture());
        Recognition recognition = saved.getValue();
        assertThat(recognition.getFrameMediaKeys()).containsExactly(first.getMediaKey(), second.getMediaKey());
        assertThat(recognition.getCandidateObjectIds()).containsExactly(CANDIDATE);
        assertThat(recognition.getVisionJobId()).isEqualTo(ticket.getValue().getJobId());
        var order = inOrder(enrollments, repository, transactions, jobs);
        order.verify(enrollments).synchronizeAll(profile, List.of(CANDIDATE));
        order.verify(repository).save(recognition);
        order.verify(transactions).commit(any());
        order.verify(jobs).dispatch(ticket.getValue().getJobId());

        assertThat(view.recognitionId()).isEqualTo(recognition.getRecognitionId());
        assertThat(view.entityName()).isEqualTo("Boat");
        assertThat(view.status()).isEqualTo(Recognition.Status.QUEUED);
        assertThat(view.outcome()).isNull();
        assertThat(view.cropUrl()).isNull();
        assertThat(view.candidates()).isEmpty();
    }

    @Test
    void theFrameCountIsBounded() {
        List<String> tooMany = IntStream.range(0, properties.getRecognition().getMaxFrames() + 1).mapToObj(i -> "k" + i).toList();

        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", null, List.of(CANDIDATE)))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid");
        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of(), List.of(CANDIDATE)))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid");
        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", tooMany, List.of(CANDIDATE)))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid")
                .hasMessageContaining("mediaKeys");
        verifyNoInteractions(profiles, uploads, tickets, repository, jobs, enrollments);
    }

    @Test
    void theCandidateCountIsBounded() {
        properties.getRecognition().setMaxCandidates(2);
        List<UUID> tooMany = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("k"), null))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid");
        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("k"), Collections.emptyList()))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid");
        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("k"), tooMany))
                .hasFieldOrPropertyWithValue("errorId", "ai.recognition.invalid")
                .hasMessageContaining("candidateObjectIds");
        verifyNoInteractions(profiles, uploads, tickets, repository, jobs, enrollments);
    }

    @Test
    void anUnknownCandidateIsInvalid() {
        UUID stranger = UUID.randomUUID();
        when(subjects.subjectExists(ORG, "Boat", stranger)).thenReturn(false);

        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("k"), List.of(CANDIDATE, stranger)))
                .isInstanceOfSatisfying(AiRequestException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.INVALID);
                    assertThat(e.getErrorId()).isEqualTo("ai.recognition.candidate-unknown");
                });
        verifyNoInteractions(uploads, tickets, repository, jobs, enrollments);
    }

    @Test
    void aMissingProfileIsPropagated() {
        when(profiles.require(ORG, "Crew")).thenThrow(AiRequestException.notFound("ai.profile.not-found", "none"));

        assertThatThrownBy(() -> recognitions.start(ORG, "Crew", List.of("k"), List.of(CANDIDATE)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.not-found");
        verifyNoInteractions(uploads, tickets, repository, jobs);
    }

    @Test
    void aFrameThatCannotBeClaimedOpensNoTicket() {
        when(uploads.claim(ORG, "used", MediaPurpose.RECOGNITION_FRAME))
                .thenThrow(AiRequestException.invalid("ai.media.already-used", "used"));

        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("used"), List.of(CANDIDATE)))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.already-used");
        verifyNoInteractions(tickets, repository, jobs);
    }

    @Test
    void findingAnswersTheOutcomeWithASignedCropUrl() {
        Recognition recognition = new Recognition(ORG, "Boat", List.of(UUID.randomUUID()), List.of(CANDIDATE),
                UUID.randomUUID(), Instant.now());
        recognition.withoutGallery(List.of(CANDIDATE));
        recognition.answered(CANDIDATE, 0.9, "GER 1", 0.8, "my-org/recognitions/x.jpg",
                List.of(new Recognition.RankedCandidate(CANDIDATE, 0.9, 1.0, null)), Instant.now());
        when(repository.findByOrgKeyAndRecognitionId(ORG, recognition.getRecognitionId())).thenReturn(Optional.of(recognition));

        RecognitionView view = recognitions.find(ORG, recognition.getRecognitionId());

        verify(guard).requireAccess(ORG);
        assertThat(view.status()).isEqualTo(Recognition.Status.DONE);
        assertThat(view.outcome()).isEqualTo(Recognition.Outcome.MATCHED);
        assertThat(view.objectId()).isEqualTo(CANDIDATE);
        assertThat(view.score()).isEqualTo(0.9);
        assertThat(view.observedIdentifierText()).isEqualTo("GER 1");
        assertThat(view.observedIdentifierConfidence()).isEqualTo(0.8);
        assertThat(view.cropUrl()).isEqualTo("http://media/my-org/recognitions/x.jpg");
        assertThat(view.candidates()).singleElement().satisfies(candidate -> assertThat(candidate.getObjectId()).isEqualTo(CANDIDATE));
        assertThat(view.candidatesWithoutGallery()).containsExactly(CANDIDATE);
        assertThat(view.finishedAt()).isNotNull();
        verify(store).readUrl("my-org/recognitions/x.jpg", properties.getMedia().getReadUrlExpiry());
    }

    @Test
    void anUnknownRecognitionIsNotFoundAndAccessIsChecked() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> recognitions.find(ORG, unknown))
                .isInstanceOfSatisfying(AiRequestException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.NOT_FOUND);
                    assertThat(e.getErrorId()).isEqualTo("ai.recognition.not-found");
                });

        doThrow(new IllegalStateException("access denied")).when(guard).requireAccess(ORG);
        assertThatThrownBy(() -> recognitions.find(ORG, unknown)).hasMessage("access denied");
        assertThatThrownBy(() -> recognitions.start(ORG, "Boat", List.of("k"), List.of(CANDIDATE))).hasMessage("access denied");
    }

    @Test
    void purgingDropsExpiredRecognitionsWithTheirFramesAndCrop() {
        MediaUpload frame = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now());
        Recognition withCrop = new Recognition(ORG, "Boat", List.of(frame.getMediaKey()), List.of(CANDIDATE),
                UUID.randomUUID(), Instant.now().minusSeconds(200_000));
        withCrop.answered(CANDIDATE, 0.9, null, null, "my-org/recognitions/old.jpg", List.of(), Instant.now());
        Recognition withoutCrop = new Recognition(ORG, "Boat", List.of(), List.of(CANDIDATE),
                UUID.randomUUID(), Instant.now().minusSeconds(200_000));
        when(repository.findByCreatedAtBefore(any())).thenReturn(List.of(withCrop, withoutCrop));
        when(uploadRepository.findAllById(List.of(frame.getMediaKey()))).thenReturn(List.of(frame));

        recognitions.purgeExpired();

        verify(store).delete(frame.getObjectName());
        verify(store).delete("my-org/recognitions/old.jpg");
        verify(uploadRepository).deleteAllById(List.of(frame.getMediaKey()));
        verify(repository).deleteById(withCrop.getRecognitionId());
        verify(repository).deleteById(withoutCrop.getRecognitionId());
    }

    @Test
    void aRecognitionThatCannotBePurgedDoesNotStopTheOthers() {
        Recognition broken = new Recognition(ORG, "Boat", List.of(), List.of(CANDIDATE), UUID.randomUUID(), Instant.now());
        broken.answered(CANDIDATE, 0.9, null, null, "broken.jpg", List.of(), Instant.now());
        Recognition fine = new Recognition(ORG, "Boat", List.of(), List.of(CANDIDATE), UUID.randomUUID(), Instant.now());
        when(repository.findByCreatedAtBefore(any())).thenReturn(List.of(broken, fine));
        doThrow(new IllegalStateException("storage down")).when(store).delete("broken.jpg");

        recognitions.purgeExpired();

        verify(repository, never()).deleteById(broken.getRecognitionId());
        verify(repository).deleteById(fine.getRecognitionId());
    }
}
