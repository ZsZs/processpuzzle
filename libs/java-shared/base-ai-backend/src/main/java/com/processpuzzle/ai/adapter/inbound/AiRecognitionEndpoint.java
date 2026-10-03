package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.api.AiRecognitionApi;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.model.Enrollment;
import com.processpuzzle.ai.model.EnrollmentPhotosInput;
import com.processpuzzle.ai.model.MediaUpload;
import com.processpuzzle.ai.model.MediaUploadRequest;
import com.processpuzzle.ai.model.PageOfRecognitionProfile;
import com.processpuzzle.ai.model.RecognitionProfile;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.Enrollments;
import com.processpuzzle.ai.usecase.MediaUploads;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.core.logging.LogClass;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the profile, media and enrollment use cases. Thin by intent, like base-widget's
 * endpoint: it converts, delegates and maps, and refusals become status codes in
 * {@link AiApiExceptionHandler}.
 *
 * <p>Sessions, jobs and tracks are not implemented yet; their operations keep the generated
 * defaults, which answer 501.
 */
@RestController
@LogClass
public class AiRecognitionEndpoint implements AiRecognitionApi {

    private final RecognitionProfiles profiles;
    private final MediaUploads uploads;
    private final Enrollments enrollments;
    private final AiMapper mapper;

    public AiRecognitionEndpoint(RecognitionProfiles profiles, MediaUploads uploads, Enrollments enrollments, AiMapper mapper) {
        this.profiles = profiles;
        this.uploads = uploads;
        this.enrollments = enrollments;
        this.mapper = mapper;
    }

    // ── Profiles ───────────────────────────────────────────────────

    @Override
    public ResponseEntity<PageOfRecognitionProfile> listRecognitionProfiles(String orgKey, Integer page, Integer size) {
        Page<com.processpuzzle.ai.domain.RecognitionProfile> found =
                profiles.findAll(orgKey, PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));
        PageOfRecognitionProfile body = new PageOfRecognitionProfile();
        body.setContent(found.getContent().stream().map(mapper::toModel).toList());
        body.setTotalElements(found.getTotalElements());
        body.setTotalPages(found.getTotalPages());
        body.setNumber(found.getNumber());
        body.setSize(found.getSize());
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<RecognitionProfile> getRecognitionProfile(String orgKey, String entityName) {
        return ResponseEntity.ok(mapper.toModel(profiles.find(orgKey, entityName)));
    }

    @Override
    public ResponseEntity<RecognitionProfile> createRecognitionProfile(String orgKey, RecognitionProfileInput input) {
        var created = profiles.create(orgKey, mapper.toDraft(input));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<RecognitionProfile> updateRecognitionProfile(String orgKey, String entityName, RecognitionProfileInput input) {
        return ResponseEntity.ok(mapper.toModel(profiles.update(orgKey, entityName, mapper.toDraft(input))));
    }

    @Override
    public ResponseEntity<Void> deleteRecognitionProfile(String orgKey, String entityName) {
        profiles.delete(orgKey, entityName);
        return ResponseEntity.noContent().build();
    }

    // ── Media ──────────────────────────────────────────────────────

    @Override
    public ResponseEntity<MediaUpload> createMediaUpload(String orgKey, MediaUploadRequest request) {
        MediaPurpose purpose = request.getPurpose() == null ? null : MediaPurpose.valueOf(request.getPurpose().name());
        long size = request.getSizeBytes() == null ? 0 : request.getSizeBytes();
        var slot = uploads.create(orgKey, purpose, request.getContentType(), size);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(slot));
    }

    // ── Enrollment ─────────────────────────────────────────────────

    @Override
    public ResponseEntity<Enrollment> getEnrollment(String orgKey, String entityName, UUID objectId) {
        return ResponseEntity.ok(mapper.toModel(enrollments.find(orgKey, entityName, objectId)));
    }

    @Override
    public ResponseEntity<Enrollment> addEnrollmentPhotos(String orgKey, String entityName, UUID objectId, EnrollmentPhotosInput input) {
        var view = enrollments.addPhotos(orgKey, entityName, objectId, input.getMediaKeys());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toModel(view));
    }

    @Override
    public ResponseEntity<Void> deleteEnrollmentPhoto(String orgKey, String entityName, UUID objectId, UUID photoId) {
        enrollments.deletePhoto(orgKey, entityName, objectId, photoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteEnrollment(String orgKey, String entityName, UUID objectId) {
        enrollments.delete(orgKey, entityName, objectId);
        return ResponseEntity.noContent().build();
    }
}
