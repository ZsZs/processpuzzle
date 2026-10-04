package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.Recognition;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.model.MediaUploadRequest;
import com.processpuzzle.ai.model.RecognitionInput;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.EnrollmentView;
import com.processpuzzle.ai.usecase.Enrollments;
import com.processpuzzle.ai.usecase.MediaUploads;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.ai.usecase.RecognitionView;
import com.processpuzzle.ai.usecase.Recognitions;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class AiRecognitionEndpointTest {

    private static final String ORG = "my-org";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    private final RecognitionProfiles profiles = mock(RecognitionProfiles.class);
    private final MediaUploads uploads = mock(MediaUploads.class);
    private final Enrollments enrollments = mock(Enrollments.class);
    private final AiMapper mapper = new AiMapper();
    private final Recognitions recognitions = mock(Recognitions.class);
    private final AiRecognitionEndpoint endpoint = new AiRecognitionEndpoint(profiles, uploads, enrollments, recognitions, mapper);

    @ParameterizedTest
    @CsvSource(value = {"null,null,0,20", "2,5,2,5", "null,5,0,5", "2,null,2,20"}, nullValues = "null")
    void listsProfilesWithRequestedOrDefaultPagination(Integer page, Integer size, int expectedPage, int expectedSize) {
        PageRequest pageable = PageRequest.of(expectedPage, expectedSize);
        var profile = new RecognitionProfile(ORG, "Boat");
        when(profiles.findAll(ORG, pageable)).thenReturn(new PageImpl<>(List.of(profile), pageable, 100));

        var response = endpoint.listRecognitionProfiles(ORG, page, size);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(mapper.toModel(profile));
        assertThat(response.getBody().getTotalElements()).isEqualTo(100L);
        assertThat(response.getBody().getTotalPages()).isEqualTo(100 / expectedSize);
        assertThat(response.getBody().getNumber()).isEqualTo(expectedPage);
        assertThat(response.getBody().getSize()).isEqualTo(expectedSize);
    }

    @Test
    void getsAndCreatesProfilesUsingTheMappedDraft() {
        var profile = new RecognitionProfile(ORG, "Boat");
        var input = new RecognitionProfileInput("Boat", "Sailboat", "boat", "photos");
        when(profiles.find(ORG, "Boat")).thenReturn(profile);
        when(profiles.create(ORG, mapper.toDraft(input))).thenReturn(profile);

        var found = endpoint.getRecognitionProfile(ORG, "Boat");
        var created = endpoint.createRecognitionProfile(ORG, input);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isEqualTo(mapper.toModel(profile));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isEqualTo(mapper.toModel(profile));
        verify(profiles).create(ORG, mapper.toDraft(input));
    }

    @Test
    void updatesUseThePathEntityNameAndDeletesHaveNoBody() {
        var input = new RecognitionProfileInput("IgnoredName", "Sailboat", "boat", "photos");
        var profile = new RecognitionProfile(ORG, "Boat");
        when(profiles.update(ORG, "Boat", mapper.toDraft(input))).thenReturn(profile);

        var updated = endpoint.updateRecognitionProfile(ORG, "Boat", input);
        var deleted = endpoint.deleteRecognitionProfile(ORG, "Boat");

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isEqualTo(mapper.toModel(profile));
        verify(profiles).update(ORG, "Boat", mapper.toDraft(input));
        verify(profiles).delete(ORG, "Boat");
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleted.getBody()).isNull();
    }

    @Test
    void createsUploadSlotsWithTheRequestedPurposeAndSize() {
        var request = new MediaUploadRequest(com.processpuzzle.ai.model.MediaPurpose.RECOGNITION_FRAME,
                "boat.jpg", "image/jpeg", 100L);
        var upload = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 100, Instant.now());
        var slot = new MediaUploads.Slot(upload, "https://media/upload", Map.of("Content-Type", "image/jpeg"));
        when(uploads.create(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 100)).thenReturn(slot);

        var response = endpoint.createMediaUpload(ORG, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(slot));
        verify(uploads).create(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 100);
    }

    @Test
    void absentUploadFieldsArePassedToTheUseCaseForValidation() {
        var request = new MediaUploadRequest().contentType("image/jpeg");
        var refusal = AiRequestException.invalid("ai.media.invalid", "purpose is required.");
        when(uploads.create(ORG, null, "image/jpeg", 0)).thenThrow(refusal);

        assertThatThrownBy(() -> endpoint.createMediaUpload(ORG, request)).isSameAs(refusal);
        verify(uploads).create(ORG, null, "image/jpeg", 0);
    }

    @Test
    void getsEnrollmentsAndAcceptsSynchronizations() {
        var view = new EnrollmentView("Boat", OBJECT_ID, EnrollmentView.Status.PROCESSING, null, null, List.of(), null);
        when(enrollments.find(ORG, "Boat", OBJECT_ID)).thenReturn(view);
        when(enrollments.synchronize(ORG, "Boat", OBJECT_ID)).thenReturn(view);

        var found = endpoint.getEnrollment(ORG, "Boat", OBJECT_ID);
        var synchronized_ = endpoint.synchronizeEnrollment(ORG, "Boat", OBJECT_ID);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isEqualTo(mapper.toModel(view));
        assertThat(synchronized_.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(synchronized_.getBody()).isEqualTo(mapper.toModel(view));
        verify(enrollments).synchronize(ORG, "Boat", OBJECT_ID);
    }

    @Test
    void deletesEnrollmentsWithinTheRequestedSubject() {
        var response = endpoint.deleteEnrollment(ORG, "Boat", OBJECT_ID);

        verify(enrollments).delete(ORG, "Boat", OBJECT_ID);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    private static RecognitionView queued(UUID recognitionId) {
        return new RecognitionView(recognitionId, "Boat", Recognition.Status.QUEUED, null, null, null, null, null, null,
                List.of(), List.of(), null, Instant.parse("2026-10-04T10:00:00Z"), null);
    }

    @Test
    void startsARecognitionWithTheRequestedFramesAndCandidates() {
        UUID recognitionId = UUID.randomUUID();
        var view = queued(recognitionId);
        var input = new RecognitionInput("Boat", new LinkedHashSet<>(List.of("frame-1")), new LinkedHashSet<>(List.of(OBJECT_ID)));
        when(recognitions.start(ORG, "Boat", List.of("frame-1"), List.of(OBJECT_ID))).thenReturn(view);

        var response = endpoint.startRecognition(ORG, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(view));
        verify(recognitions).start(ORG, "Boat", List.of("frame-1"), List.of(OBJECT_ID));
    }

    @Test
    void absentRecognitionListsArePassedEmptyForValidation() {
        var input = new RecognitionInput().entityName("Boat").mediaKeys(null).candidateObjectIds(null);
        var refusal = AiRequestException.invalid("ai.recognition.invalid", "Between 1 and 5 mediaKeys are required.");
        when(recognitions.start(ORG, "Boat", List.of(), List.of())).thenThrow(refusal);

        assertThatThrownBy(() -> endpoint.startRecognition(ORG, input)).isSameAs(refusal);
    }

    @Test
    void getsARecognition() {
        UUID recognitionId = UUID.randomUUID();
        var view = queued(recognitionId);
        when(recognitions.find(ORG, recognitionId)).thenReturn(view);

        var response = endpoint.getRecognition(ORG, recognitionId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(view));
    }
}
