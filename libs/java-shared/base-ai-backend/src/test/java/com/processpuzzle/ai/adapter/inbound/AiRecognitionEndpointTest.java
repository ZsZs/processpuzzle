package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.model.EnrollmentPhotosInput;
import com.processpuzzle.ai.model.MediaUploadRequest;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.EnrollmentView;
import com.processpuzzle.ai.usecase.Enrollments;
import com.processpuzzle.ai.usecase.MediaUploads;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import java.time.Instant;
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
    private final AiRecognitionEndpoint endpoint = new AiRecognitionEndpoint(profiles, uploads, enrollments, mapper);

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
        var input = new RecognitionProfileInput("Boat", "Sailboat", "boat");
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
        var input = new RecognitionProfileInput("IgnoredName", "Sailboat", "boat");
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
        var request = new MediaUploadRequest(com.processpuzzle.ai.model.MediaPurpose.ENROLLMENT_PHOTO,
                "boat.jpg", "image/jpeg", 100L);
        var upload = new MediaUpload(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 100, Instant.now());
        var slot = new MediaUploads.Slot(upload, "https://media/upload", Map.of("Content-Type", "image/jpeg"));
        when(uploads.create(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 100)).thenReturn(slot);

        var response = endpoint.createMediaUpload(ORG, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(slot));
        verify(uploads).create(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 100);
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
    void getsEnrollmentsAndAcceptsPhotoSubmissions() {
        var view = new EnrollmentView("Boat", OBJECT_ID, EnrollmentView.Status.PROCESSING, null, null, List.of(), null);
        List<String> keys = List.of("first", "second");
        when(enrollments.find(ORG, "Boat", OBJECT_ID)).thenReturn(view);
        when(enrollments.addPhotos(ORG, "Boat", OBJECT_ID, keys)).thenReturn(view);

        var found = endpoint.getEnrollment(ORG, "Boat", OBJECT_ID);
        var added = endpoint.addEnrollmentPhotos(ORG, "Boat", OBJECT_ID, new EnrollmentPhotosInput().mediaKeys(keys));

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isEqualTo(mapper.toModel(view));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(added.getBody()).isEqualTo(mapper.toModel(view));
        verify(enrollments).addPhotos(ORG, "Boat", OBJECT_ID, keys);
    }

    @Test
    void deletesPhotosAndEnrollmentsWithinTheRequestedSubject() {
        UUID photoId = UUID.randomUUID();

        var photoResponse = endpoint.deleteEnrollmentPhoto(ORG, "Boat", OBJECT_ID, photoId);
        var enrollmentResponse = endpoint.deleteEnrollment(ORG, "Boat", OBJECT_ID);

        verify(enrollments).deletePhoto(ORG, "Boat", OBJECT_ID, photoId);
        verify(enrollments).delete(ORG, "Boat", OBJECT_ID);
        assertThat(photoResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(photoResponse.getBody()).isNull();
        assertThat(enrollmentResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(enrollmentResponse.getBody()).isNull();
    }
}
