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
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Arrays;
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
    private RecognitionProfileRepository profileRepository;
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
        profileRepository = mock(RecognitionProfileRepository.class);
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
        profile.setGalleryAttributeKey("photos");
        when(profiles.require(ORG, "Boat")).thenReturn(profile);
        when(profileRepository.findByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(Optional.of(profile));
        when(subjects.subjectExists(ORG, "Boat", OBJECT_ID)).thenReturn(true);
        when(subjects.photoUrl(anyString(), any())).thenAnswer(call -> "http://artifacts/" + call.getArgument(0));
        when(store.readUrl(anyString(), any())).thenAnswer(call -> "http://media/" + call.getArgument(0));
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));
        var provider = new StaticListableBeanFactory(Map.of("subjects", subjects)).getBeanProvider(SubjectDirectory.class);
        enrollments = new Enrollments(profiles, profileRepository, photos, tickets, jobs, stores, provider, guard,
                transactions, new AiProperties());
    }

    private static EnrollmentPhoto photo(EnrollmentPhotoStatus status) {
        return photo(UUID.randomUUID().toString(), status);
    }

    private static EnrollmentPhoto photo(String photoRef, EnrollmentPhotoStatus status) {
        EnrollmentPhoto photo = new EnrollmentPhoto(ORG, "Boat", OBJECT_ID, photoRef, Instant.now());
        if (status == EnrollmentPhotoStatus.ENROLLED) {
            photo.enrolled("crop-" + photoRef, "model", new byte[4], null);
        } else if (status != EnrollmentPhotoStatus.PENDING) {
            photo.rejected(status, null);
        }
        return photo;
    }

    private void subjectHas(String... photoRefs) {
        when(subjects.photos(ORG, "Boat", OBJECT_ID, "photos")).thenReturn(Arrays.stream(photoRefs)
                .map(ref -> new SubjectDirectory.SubjectPhoto(ref, "image/jpeg")).toList());
    }

    private void galleryHas(EnrollmentPhoto... gallery) {
        when(photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(ORG, "Boat", OBJECT_ID)).thenReturn(List.of(gallery));
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
        EnrollmentPhoto enrolled = new EnrollmentPhoto(ORG, "Boat", OBJECT_ID, "artifact-1", ADDED_AT);
        enrolled.enrolled("my-org/crops/boat.jpg", "dinov2-small", new byte[] {1}, "GER 1284");
        EnrollmentPhoto rejected = new EnrollmentPhoto(ORG, "Boat", OBJECT_ID, "artifact-2", ADDED_AT.plusSeconds(10));
        rejected.rejected(EnrollmentPhotoStatus.NO_SUBJECT, "no boat");
        galleryHas(enrolled, rejected);

        EnrollmentView view = enrollments.find(ORG, "Boat", OBJECT_ID);

        assertThat(view.status()).isEqualTo(EnrollmentView.Status.READY);
        assertThat(view.identifierText()).isEqualTo("GER 1234");
        assertThat(view.embeddingModel()).isEqualTo("dinov2-small");
        assertThat(view.updatedAt()).isEqualTo(rejected.getAddedAt());
        assertThat(view.photos()).hasSize(2);
        assertThat(view.photos().getFirst()).isEqualTo(new EnrollmentView.Photo(enrolled.getPhotoId(), "artifact-1",
                EnrollmentPhotoStatus.ENROLLED, "http://artifacts/artifact-1",
                "http://media/my-org/crops/boat.jpg", "GER 1284", true, null, ADDED_AT));
        assertThat(view.photos().getLast().cropUrl()).isNull();
        assertThat(view.photos().getLast().failureReason()).isEqualTo("no boat");
        assertThat(view.photos().getLast().identifierMismatch()).isFalse();
        verify(subjects).photoUrl("artifact-1", new AiProperties().getMedia().getReadUrlExpiry());
    }

    @Test
    void aPhotoUrlTheDirectoryCannotSignLeavesTheEntryWithoutOne() {
        when(subjects.photoUrl(anyString(), any())).thenThrow(new UnsupportedOperationException("no photos"));
        galleryHas(photo("artifact-1", EnrollmentPhotoStatus.PENDING));

        EnrollmentView view = enrollments.find(ORG, "Boat", OBJECT_ID);

        assertThat(view.photos()).singleElement().satisfies(entry -> assertThat(entry.photoUrl()).isNull());
    }

    @Test
    void synchronizingEnrollsNewPhotosUnderOneTicketAndCommitsBeforeDispatching() {
        subjectHas("artifact-1", "artifact-2", "artifact-1");

        enrollments.synchronize(ORG, "Boat", OBJECT_ID);

        verify(guard, atLeastOnce()).requireAccess(ORG);
        ArgumentCaptor<VisionJobTicket> ticket = ArgumentCaptor.forClass(VisionJobTicket.class);
        verify(tickets).save(ticket.capture());
        assertThat(ticket.getValue().getKind()).isEqualTo(VisionJobTicket.Kind.ENROLLMENT);
        ArgumentCaptor<List<EnrollmentPhoto>> saved = ArgumentCaptor.captor();
        verify(photos).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(EnrollmentPhoto::getPhotoRef).containsExactly("artifact-1", "artifact-2");
        assertThat(saved.getValue()).allSatisfy(photo -> {
            assertThat(photo.getOrgKey()).isEqualTo(ORG);
            assertThat(photo.getObjectId()).isEqualTo(OBJECT_ID);
            assertThat(photo.getStatus()).isEqualTo(EnrollmentPhotoStatus.PENDING);
            assertThat(photo.getVisionJobId()).isEqualTo(ticket.getValue().getJobId());
        });
        var order = inOrder(photos, transactions, jobs);
        order.verify(photos).saveAll(saved.getValue());
        order.verify(transactions).commit(any());
        order.verify(jobs).dispatch(ticket.getValue().getJobId());
        verifyNoInteractions(store);
    }

    @Test
    void synchronizingDropsGonePhotosAndDeletesOnlyTheirCrops() {
        EnrollmentPhoto kept = photo("artifact-1", EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto gone = photo("artifact-2", EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto goneWithoutCrop = photo("artifact-3", EnrollmentPhotoStatus.NO_SUBJECT);
        galleryHas(kept, gone, goneWithoutCrop);
        subjectHas("artifact-1");

        enrollments.synchronize(ORG, "Boat", OBJECT_ID);

        var order = inOrder(photos, transactions, store);
        order.verify(photos).deleteAll(List.of(gone, goneWithoutCrop));
        order.verify(transactions).commit(any());
        order.verify(store).delete("crop-artifact-2");
        verify(store, never()).delete("crop-artifact-1");
        verify(photos, never()).saveAll(any());
        verifyNoInteractions(tickets, jobs);
    }

    @Test
    void anUnchangedGalleryIsANoOp() {
        galleryHas(photo("artifact-1", EnrollmentPhotoStatus.ENROLLED));
        subjectHas("artifact-1");

        enrollments.synchronize(ORG, "Boat", OBJECT_ID);

        verify(photos).deleteAll(List.of());
        verify(photos, never()).saveAll(any());
        verify(store, never()).delete(anyString());
        verifyNoInteractions(tickets, jobs);
    }

    @Test
    void aProfileWithoutGalleryAttributeEnrollsNothing() {
        profile.setGalleryAttributeKey(null);

        enrollments.synchronize(ORG, "Boat", OBJECT_ID);

        verify(subjects, never()).photos(anyString(), anyString(), any(), anyString());
        verifyNoInteractions(tickets, jobs);
    }

    @Test
    void synchronizingAnUnknownSubjectIsNotFound() {
        when(subjects.subjectExists(ORG, "Boat", OBJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> enrollments.synchronize(ORG, "Boat", OBJECT_ID))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.subject-not-found");
        verifyNoInteractions(tickets, jobs, store);
        verify(transactions).rollback(any());
    }

    @Test
    void aChangedSubjectIsSynchronizedAndDispatchedOffTheCallingThread() {
        EnrollmentPhoto gone = photo("artifact-0", EnrollmentPhotoStatus.ENROLLED);
        galleryHas(gone);
        subjectHas("artifact-1");

        enrollments.subjectChanged(ORG, "Boat", OBJECT_ID);

        ArgumentCaptor<VisionJobTicket> ticket = ArgumentCaptor.forClass(VisionJobTicket.class);
        verify(tickets).save(ticket.capture());
        verify(jobs).dispatchLater(ticket.getValue().getJobId());
        verify(jobs, never()).dispatch(any());
        verify(store).delete("crop-artifact-0");
        verifyNoInteractions(guard, profiles);
    }

    @Test
    void aChangedSubjectOfATypeWithoutAProfileIsIgnored() {
        when(profileRepository.findByOrgKeyAndEntityName(ORG, "Crew")).thenReturn(Optional.empty());

        enrollments.subjectChanged(ORG, "Crew", OBJECT_ID);

        verifyNoInteractions(subjects, photos, tickets, store);
        verify(jobs).dispatchLater(null);
        verify(jobs, never()).dispatch(any());
    }

    @Test
    void aDeletedSubjectLosesItsGalleryAndCropsButNotItsPhotos() {
        EnrollmentPhoto enrolled = photo("artifact-1", EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto pending = photo("artifact-2", EnrollmentPhotoStatus.PENDING);
        galleryHas(enrolled, pending);

        enrollments.subjectDeleted(ORG, "Boat", OBJECT_ID);

        var order = inOrder(photos, transactions, store);
        order.verify(photos).deleteAll(List.of(enrolled, pending));
        order.verify(transactions).commit(any());
        order.verify(store).delete("crop-artifact-1");
        verify(store).delete(anyString());
        verifyNoInteractions(guard, subjects);
    }

    @Test
    void aDeletedUnknownSubjectIsHarmless() {
        enrollments.subjectDeleted(ORG, "Boat", OBJECT_ID);

        verify(photos).deleteAll(List.of());
        verifyNoInteractions(store);
    }

    @Test
    void synchronizingAllCandidatesContinuesPastAFailure() {
        UUID broken = UUID.randomUUID();
        when(subjects.photos(ORG, "Boat", broken, "photos")).thenThrow(new IllegalStateException("directory down"));
        subjectHas("artifact-1");

        enrollments.synchronizeAll(profile, List.of(broken, OBJECT_ID));

        ArgumentCaptor<VisionJobTicket> ticket = ArgumentCaptor.forClass(VisionJobTicket.class);
        verify(tickets).save(ticket.capture());
        verify(jobs).dispatch(ticket.getValue().getJobId());
    }

    @Test
    void aCropThatCannotBeDeletedDoesNotFailTheDeletion() {
        EnrollmentPhoto first = photo("artifact-1", EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto second = photo("artifact-2", EnrollmentPhotoStatus.ENROLLED);
        galleryHas(first, second);
        doThrow(new IllegalStateException("storage down")).when(store).delete("crop-artifact-1");

        enrollments.delete(ORG, "Boat", OBJECT_ID);

        verify(store).delete("crop-artifact-2");
    }

    @Test
    void deletingAnEnrollmentDiscardsOnlyExistingCrops() {
        EnrollmentPhoto enrolled = photo("artifact-1", EnrollmentPhotoStatus.ENROLLED);
        EnrollmentPhoto pending = photo("artifact-2", EnrollmentPhotoStatus.PENDING);
        galleryHas(enrolled, pending);

        enrollments.delete(ORG, "Boat", OBJECT_ID);

        verify(guard).requireAccess(ORG);
        var order = inOrder(photos, transactions, store);
        order.verify(photos).deleteAll(List.of(enrolled, pending));
        order.verify(transactions).commit(any());
        order.verify(store).delete("crop-artifact-1");
        verify(store).delete(anyString());
        verifyNoInteractions(subjects);
    }

    @Test
    void aMissingEnrollmentCannotBeDeleted() {
        assertThatThrownBy(() -> enrollments.delete(ORG, "Boat", OBJECT_ID))
                .hasFieldOrPropertyWithValue("errorId", "ai.enrollment.not-found");
        verifyNoInteractions(store);
        verify(photos, never()).deleteAll(any());
    }

    @Test
    void deniedAccessPreventsReadingSynchronizingAndDeleting() {
        doThrow(new IllegalStateException("access denied")).when(guard).requireAccess(ORG);

        assertThatThrownBy(() -> enrollments.find(ORG, "Boat", OBJECT_ID)).hasMessage("access denied");
        assertThatThrownBy(() -> enrollments.synchronize(ORG, "Boat", OBJECT_ID)).hasMessage("access denied");
        assertThatThrownBy(() -> enrollments.delete(ORG, "Boat", OBJECT_ID)).hasMessage("access denied");
        verifyNoInteractions(profiles, photos, tickets, jobs, store, transactions);
    }
}
