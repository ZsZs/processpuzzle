package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.model.Enrollment;
import com.processpuzzle.ai.model.EnrollmentPhoto;
import com.processpuzzle.ai.model.EnrollmentPhotoStatus;
import com.processpuzzle.ai.model.EnrollmentStatus;
import com.processpuzzle.ai.model.MediaUpload;
import com.processpuzzle.ai.model.Recognition;
import com.processpuzzle.ai.model.RecognitionCandidate;
import com.processpuzzle.ai.model.RecognitionOutcome;
import com.processpuzzle.ai.model.RecognitionStatus;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.EnrollmentView;
import com.processpuzzle.ai.usecase.MediaUploads;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.ai.usecase.RecognitionView;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Converts between the generated API models and this module's own types. Enums translate by name. */
@Component
public class AiMapper {

    public RecognitionProfiles.Draft toDraft(RecognitionProfileInput input) {
        return new RecognitionProfiles.Draft(
                input.getEntityName(),
                input.getName(),
                input.getDescription(),
                input.getDetectorClass(),
                input.getGalleryAttributeKey(),
                input.getIdentifierAttributeKey(),
                input.getIdentifierPattern(),
                input.getMatching() == null ? null : toDomain(input.getMatching()));
    }

    public com.processpuzzle.ai.model.RecognitionProfile toModel(RecognitionProfile profile) {
        MatchingSettings matching = profile.getMatching() == null ? MatchingSettings.defaults() : profile.getMatching();
        com.processpuzzle.ai.model.RecognitionProfile model = new com.processpuzzle.ai.model.RecognitionProfile();
        model.setEntityName(profile.getEntityName());
        model.setName(profile.getName());
        model.setDescription(profile.getDescription());
        model.setDetectorClass(profile.getDetectorClass());
        model.setGalleryAttributeKey(profile.getGalleryAttributeKey());
        model.setIdentifierAttributeKey(profile.getIdentifierAttributeKey());
        model.setIdentifierPattern(profile.getIdentifierPattern());
        model.setMatching(new com.processpuzzle.ai.model.MatchingSettings()
                .identifierWeight(matching.getIdentifierWeight())
                .acceptScore(matching.getAcceptScore())
                .acceptMargin(matching.getAcceptMargin())
                .sampleFps(matching.getSampleFps()));
        model.setOrgKey(profile.getOrgKey());
        model.setVersion(profile.getVersion());
        model.setCreatedAt(offset(profile.getCreatedAt()));
        model.setUpdatedAt(offset(profile.getUpdatedAt()));
        return model;
    }

    public MediaUpload toModel(MediaUploads.Slot slot) {
        return new MediaUpload()
                .mediaKey(slot.upload().getMediaKey().toString())
                .uploadUrl(URI.create(slot.uploadUrl()))
                .requiredHeaders(slot.requiredHeaders())
                .expiresAt(offset(slot.upload().getExpiresAt()));
    }

    public Enrollment toModel(EnrollmentView view) {
        Enrollment model = new Enrollment()
                .entityName(view.entityName())
                .objectId(view.objectId())
                .status(EnrollmentStatus.valueOf(view.status().name()))
                .identifierText(view.identifierText())
                .embeddingModel(view.embeddingModel())
                .updatedAt(offset(view.updatedAt()));
        model.setPhotos(view.photos().stream().map(this::toModel).toList());
        return model;
    }

    private EnrollmentPhoto toModel(EnrollmentView.Photo photo) {
        return new EnrollmentPhoto()
                .photoId(photo.photoId())
                .photoRef(photo.photoRef())
                .status(EnrollmentPhotoStatus.valueOf(photo.status().name()))
                .photoUrl(photo.photoUrl() == null ? null : URI.create(photo.photoUrl()))
                .cropUrl(photo.cropUrl() == null ? null : URI.create(photo.cropUrl()))
                .observedIdentifierText(photo.observedIdentifierText())
                .identifierMismatch(photo.identifierMismatch())
                .failureReason(photo.failureReason())
                .addedAt(offset(photo.addedAt()));
    }

    public Recognition toModel(RecognitionView view) {
        Recognition model = new Recognition()
                .recognitionId(view.recognitionId())
                .entityName(view.entityName())
                .status(RecognitionStatus.valueOf(view.status().name()))
                .outcome(view.outcome() == null ? null : RecognitionOutcome.valueOf(view.outcome().name()))
                .objectId(view.objectId())
                .score(view.score())
                .observedIdentifierText(view.observedIdentifierText())
                .observedIdentifierConfidence(view.observedIdentifierConfidence())
                .cropUrl(view.cropUrl() == null ? null : URI.create(view.cropUrl()))
                .failureReason(view.failureReason())
                .createdAt(offset(view.createdAt()))
                .finishedAt(offset(view.finishedAt()));
        model.setCandidates(view.candidates().stream()
                .map(candidate -> new RecognitionCandidate()
                        .objectId(candidate.getObjectId())
                        .score(candidate.getScore())
                        .identifierScore(candidate.getIdentifierScore())
                        .embeddingScore(candidate.getEmbeddingScore()))
                .toList());
        model.setCandidatesWithoutGallery(view.candidatesWithoutGallery());
        return model;
    }

    /**
     * A field the client left out takes the contract's default. The generated model already fills in
     * schema defaults, so null here only means an explicit null.
     */
    private static MatchingSettings toDomain(com.processpuzzle.ai.model.MatchingSettings model) {
        return new MatchingSettings(
                orDefault(model.getIdentifierWeight(), MatchingSettings.DEFAULT_IDENTIFIER_WEIGHT),
                orDefault(model.getAcceptScore(), MatchingSettings.DEFAULT_ACCEPT_SCORE),
                orDefault(model.getAcceptMargin(), MatchingSettings.DEFAULT_ACCEPT_MARGIN),
                orDefault(model.getSampleFps(), MatchingSettings.DEFAULT_SAMPLE_FPS));
    }

    private static double orDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
