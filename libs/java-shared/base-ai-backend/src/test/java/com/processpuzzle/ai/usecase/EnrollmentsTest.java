package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class EnrollmentsTest {

    private static final String ORG = "my-org";
    private static final UUID OBJECT_ID = UUID.randomUUID();
    private static final Instant ADDED_AT = Instant.parse("2026-10-03T10:00:00Z");

    private RecognitionProfiles profiles;
    private MediaUploads uploads;
    private EnrollmentPhotoRepository photos;
    private VisionJobTicketRepository tickets;
    private VisionJobs jobs;
    private MediaStore store;
    private SubjectDirectory subjects;
    private OrganizationGuard guard;
    private PlatformTransactionManager transactions;
    private RecognitionProfile profile;
    private Enrollments enrollments;

    @BeforeEach
    void setUp() {
        profiles = mock(RecognitionProfiles.class);
        uploads = mock(MediaUploads.class);
        photos = mock(EnrollmentPhotoRepository.class);
        tickets = mock(VisionJobTicketRepository.class);
        jobs = mock(VisionJobs.class);
        store = mock(MediaStore.class);
        MediaStores stores = mock(MediaStores.class);
        when(stores.get()).thenReturn(store);
        subjects = mock(SubjectDirectory.class);
        guard = mock(OrganizationGuard.class);
        transactions = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(transactionStatus);
        profile = new RecognitionProfile(ORG, "Boat");
        when(profiles.require(ORG, "Boat")).thenReturn(profile);
        when(subjects.subjectExists(ORG, "Boat", OBJECT_ID)).thenReturn(true);
        when(store.readUrl(anyString(), any())).thenAnswer(call -> "http://media/" + call.getArgument(0));
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));
        var provider = new StaticListableBeanFactory(Map.of("subjects", subjects)).getBeanProvider(SubjectDirectory.class);
        enrollments = new Enrollments(profiles, uploads, photos, tickets, jobs, stores, provider, guard,
                transactions, new AiProperties());
    }

    private static EnrollmentPhoto photo(EnrollmentPhotoStatus status) {
        MediaUpload upload = new MediaUpload("my-org", MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 1, Instant.now());
        EnrollmentPhoto photo = new EnrollmentPhoto(upload, "Boat", UUID.randomUUID(), Instant.now());
        if (status == EnrollmentPhotoStatus.ENROLLED) {
            photo.enrolled("crop", "model", new byte[4], null);
        } else if (status != EnrollmentPhotoStatus.PENDING) {
            photo.rejected(status, null);
        }
        return photo;
    }

    @Test
    void statusSummarizesTheGallery() {
        assertThat(Enrollments.status(List.of())).isEqualTo(EnrollmentView.Status.NOT_ENROLLED);
        assertThat(Enrollments.status(List.of(photo(EnrollmentPhotoStatus.ENROLLED), photo(EnrollmentPhotoStatus.PENDING))))
                .isEqualTo(EnrollmentView.Status.PROCESSING);
        assertThat(Enrollments.status(List.of(photo(EnrollmentPhotoStatus.ENROLLED), photo(EnrollmentPhotoStatus.NO_SUBJECT))))
                .isEqualTo(EnrollmentView.Status.READY);
        assertThat(Enrollments.status(List.of(photo(EnrollmentPhotoStatus.AMBIGUOUS))))
                .isEqualTo(EnrollmentView.Status.FAILED);
    }

    @Test
    void aMismatchIgnoresCaseAndSpacing() {
        assertThat(Enrollments.mismatch("ger1234", "GER 1234")).isFalse();
        assertThat(Enrollments.mismatch("GER 1284", "GER 1234")).isTrue();
        assertThat(Enrollments.mismatch(null, "GER 1234")).isFalse();
        assertThat(Enrollments.mismatch("GER 1234", null)).isFalse();
    }

    @Test
    void anEmptyGalleryHasNoIdentifierModelOrTimestamp() {
        EnrollmentView view = enrollments.find(ORG, "Boat", OBJECT_ID);

        assertThat(view.entityName()).isEqualTo("Boat");
        assertThat(view.objectId()).isEqualTo(OBJECT_ID);
        assertThat(view.status()).isEqualTo(EnrollmentView.Status.NOT_ENROLLED);
        assertThat(view.photos()).isEmpty();
        assertThat(view.identifierText()).isNull();
        assertThat(view.embeddingModel()).isNull();
        assertThat(view.updatedAt()).isNull();
        verify(guard).requireAccess(ORG);
        verify(subjects, never()).identifier(anyString(), anyString(), any(), anyString());
        verifyNoInteractions(store);
    }

    @Test
    void aGalleryIncludesSignedUrlsIdentifiersFailuresAndTheLatestTimestamp() {
        profile.setIdentifierAttributeKey("sailNumber");
        when(subjects.identifier(ORG, "Boat", OBJECT_ID, "sailNumber")).thenReturn(Optional.of("GER 1234"));
        EnrollmentPhoto enrolled = new EnrollmentPhoto(upload(), "Boat", OBJECT_ID, ADDED_AT);
        enrolled.enrolled("my-org/crops/boat.jpg", "dinov2-small", new byte[] {1}, "GER 1284");
        EnrollmentPhoto rejected = new EnrollmentPhoto(upload(), "Boat", OBJECT_ID, ADDED_AT.plusSeconds(10));
        rejected.rejected(EnrollmentPhotoStatus.NO_SUBJECT, "no boat");
        when(photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(ORG, "Boat", OBJECT_ID))
                .thenReturn(List.of(enrolled, rejected));

        EnrollmentView view = enrollments.find(ORG, "Boat", OBJECT_ID);

        assertThat(view.status()).isEqualTo(EnrollmentView.Status.READY);
        assertThat(view.identifierText()).isEqualTo("GER 1234");
        assertThat(view.embeddingModel()).isEqualTo("dinov2-small");
        assertThat(view.updatedAt()).isEqualTo(rejected.getAddedAt());
        assertThat(view.photos()).hasSize(2);
        assertThat(view.photos().getFirst()).isEqualTo(new EnrollmentView.Photo(enrolled.getPhotoId(),
                EnrollmentPhotoStatus.ENROLLED, "http://media/" + enrolled.getPhotoObjectName(),
                "http://media/my-org/crops/boat.jpg", "GER 1284", true, null, ADDED_AT));
        assertThat(view.photos().getLast().cropUrl()).isNull();
        assertThat(view.photos().getLast().failureReason()).isEqualTo("no boat");
        assertThat(view.photos().getLast().identifierMismatch()).isFalse();
        verify(store).readUrl(enrolled.getPhotoObjectName(), new AiProperties().getMedia().getReadUrlExpiry());
    }

    @Test
    void addingPhotosDeduplicatesMediaKeysAndCommitsBeforeDispatching() {
        MediaUpload upload = upload();
        String key = upload.getMediaKey().toString();
        when(uploads.claim(ORG, key, MediaPurpose.ENROLLMENT_PHOTO)).thenReturn(upload);

        enrollments.addPhotos(ORG, "Boat", OBJECT_ID, List.of(key, key));

        verify(uploads).claim(ORG, key, MediaPurpose.ENROLLMENT_PHOTO);
        ArgumentCaptor<List<EnrollmentPhoto>> saved = ArgumentCaptor.captor();
        verify(photos).saveAll(saved.capture());
        ArgumentCaptor<VisionJobTicket> ticket = ArgumentCaptor.forClass(VisionJobTicket.class);
        verify(tickets).save(ticket.capture());
        assertThat(saved.getValue()).singleElement().satisfies(photo -> {
            assertThat(photo.getMediaKey()).isEqualTo(upload.getMediaKey());
            assertThat(photo.getOrgKey()).isEqualTo(ORG);
            assertThat(photo.getObjectId()).isEqualTo(OBJECT_ID);
            assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.PENDING);
            assertThat(photo.getVisionJobId()).isEqualTo(ticket.getValue().getJobId());
        });
        var order = inOrder(photos, transactions, jobs);
        order.verify(photos).saveAll(saved.getValue());
        order.verify(transactions).commit(any());
        order.verify(jobs).dispatch(ticket.getValue().getJobId());
    }

    @Test
    void retryingAnExistingMediaKeyDoesNotCreateAnotherJob() {
        MediaUpload upload = upload();
        String key = upload.getMediaKey().toString();
        when(uploads.claim(ORG, key, MediaPurpose.ENROLLMENT_PHOTO)).thenReturn(upload);
        when(photos.existsByOrgKeyAndMediaKey(ORG, upload.getMediaKey())).thenReturn(true);

        enrollments.addPhotos(ORG, "Boat", OBJECT_ID, List.of(key));

        verify(photos, never()).saveAll(any());
        verifyNoInteractions(tickets, jobs);
    }

    @Test
    void emptyRequestsAndUnknownSubjectsAreRejectedBeforeClaimingMedia() {
        assertThatThrownBy(() -> enrollments.addPhotos(ORG, "Boat", OBJECT_ID, null))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.invalid");
        assertThatThrownBy(() -> enrollments.addPhotos(ORG, "Boat", OBJECT_ID, List.of()))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.invalid");
        when(subjects.subjectExists(ORG, "Boat", OBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> enrollments.addPhotos(ORG, "Boat", OBJECT_ID, List.of("key")))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.subject-not-found");
        verifyNoInteractions(uploads, tickets, jobs, store);
        verify(transactions).rollback(any());
    }

    @Test
    void deletingAPhotoCommitsBeforeRemovingItsOriginalAndCrop() {
        EnrollmentPhoto photo = photo(EnrollmentPhotoStatus.ENROLLED);
        when(photos.findByOrgKeyAndEntityNameAndObjectIdAndPhotoId(ORG, "Boat", OBJECT_ID, photo.getPhotoId()))
                .thenReturn(Optional.of(photo));

        enrollments.deletePhoto(ORG, "Boat", OBJECT_ID, photo.getPhotoId());

        var order = inOrder(guard, photos, transactions, store);
        order.verify(guard).requireAccess(ORG);
        order.verify(photos).delete(photo);
        order.verify(transactions).commit(any());
        order.verify(store).delete(photo.getPhotoObjectName());
        order.verify(store).delete("crop");
    }

    @Test
    void deletingAnEnrollmentRemovesEveryOriginalAndOnlyExistingCrops() {
        EnrollmentPhoto enrolled = photo(EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto pending = photo(EnrollmentPhotoStatus.PENDING);
        List<EnrollmentPhoto> gallery = List.of(enrolled, pending);
        when(photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(ORG, "Boat", OBJECT_ID)).thenReturn(gallery);

        enrollments.delete(ORG, "Boat", OBJECT_ID);

        verify(guard).requireAccess(ORG);
        var order = inOrder(photos, transactions, store);
        order.verify(photos).deleteAll(gallery);
        order.verify(transactions).commit(any());
        order.verify(store).delete(enrolled.getPhotoObjectName());
        order.verify(store).delete("crop");
        order.verify(store).delete(pending.getPhotoObjectName());
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingPhotosAndEnrollmentsCannotDeleteStoredObjects() {
        assertThatThrownBy(() -> enrollments.deletePhoto(ORG, "Boat", OBJECT_ID, UUID.randomUUID()))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.photo-not-found");
        assertThatThrownBy(() -> enrollments.delete(ORG, "Boat", OBJECT_ID))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.not-found");
        verifyNoInteractions(store);
        verify(photos, never()).delete(any());
        verify(photos, never()).deleteAll(any());
    }

    @Test
    void deniedAccessPreventsReadingRegisteringAndDeleting() {
        doThrow(new IllegalStateException("access denied")).when(guard).requireAccess(ORG);
        UUID photoId = UUID.randomUUID();

        assertThatThrownBy(() -> enrollments.find(ORG, "Boat", OBJECT_ID)).hasMessage("access denied");
        assertThatThrownBy(() -> enrollments.addPhotos(ORG, "Boat", OBJECT_ID, List.of("key"))).hasMessage("access denied");
        assertThatThrownBy(() -> enrollments.deletePhoto(ORG, "Boat", OBJECT_ID, photoId)).hasMessage("access denied");
        assertThatThrownBy(() -> enrollments.delete(ORG, "Boat", OBJECT_ID)).hasMessage("access denied");
        verifyNoInteractions(profiles, photos, uploads, tickets, jobs, store, transactions);
    }

    private static MediaUpload upload() {
        return new MediaUpload(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 100, ADDED_AT.plusSeconds(3600));
    }
}
