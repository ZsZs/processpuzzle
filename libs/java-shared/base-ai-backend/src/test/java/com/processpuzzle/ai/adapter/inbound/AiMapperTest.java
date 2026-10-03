package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.model.RecognitionProfileInput;
import com.processpuzzle.ai.usecase.EnrollmentView;
import com.processpuzzle.ai.usecase.MediaUploads;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AiMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private final AiMapper mapper = new AiMapper();

    @Test
    void draftsPreserveEveryWritableFieldAndCustomMatchingSettings() {
        var input = new RecognitionProfileInput("Boat", "Sailboats", "boat")
                .description("Race entries").identifierAttributeKey("sailNumber").identifierPattern("GER [0-9]+")
                .matching(new com.processpuzzle.ai.model.MatchingSettings()
                        .identifierWeight(0.7).acceptScore(0.8).acceptMargin(0.2).sampleFps(5.0));

        var draft = mapper.toDraft(input);

        assertThat(draft.entityName()).isEqualTo("Boat");
        assertThat(draft.name()).isEqualTo("Sailboats");
        assertThat(draft.description()).isEqualTo("Race entries");
        assertThat(draft.detectorClass()).isEqualTo("boat");
        assertThat(draft.identifierAttributeKey()).isEqualTo("sailNumber");
        assertThat(draft.identifierPattern()).isEqualTo("GER [0-9]+");
        assertThat(draft.matching()).usingRecursiveComparison().isEqualTo(new MatchingSettings(0.7, 0.8, 0.2, 5));
    }

    @Test
    void omittedMatchingAndExplicitNullFieldsKeepTheContractDefaults() {
        var input = new RecognitionProfileInput("Boat", "Sailboats", "boat");
        assertThat(mapper.toDraft(input).matching()).isNull();

        input.matching(new com.processpuzzle.ai.model.MatchingSettings()
                .identifierWeight(null).acceptScore(null).acceptMargin(null).sampleFps(null));
        assertThat(mapper.toDraft(input).matching()).usingRecursiveComparison().isEqualTo(MatchingSettings.defaults());
    }

    @Test
    void profileResponsesPreserveMetadataAndMatchingSettings() {
        var profile = new RecognitionProfile("my-org", "Boat");
        profile.setName("Sailboats");
        profile.setDescription("Race entries");
        profile.setDetectorClass("boat");
        profile.setIdentifierAttributeKey("sailNumber");
        profile.setIdentifierPattern("GER [0-9]+");
        profile.setMatching(new MatchingSettings(0.7, 0.8, 0.2, 5));

        var model = mapper.toModel(profile);

        assertThat(model.getOrgKey()).isEqualTo("my-org");
        assertThat(model.getEntityName()).isEqualTo("Boat");
        assertThat(model.getName()).isEqualTo("Sailboats");
        assertThat(model.getDescription()).isEqualTo("Race entries");
        assertThat(model.getDetectorClass()).isEqualTo("boat");
        assertThat(model.getIdentifierAttributeKey()).isEqualTo("sailNumber");
        assertThat(model.getIdentifierPattern()).isEqualTo("GER [0-9]+");
        assertThat(model.getMatching()).isEqualTo(new com.processpuzzle.ai.model.MatchingSettings()
                .identifierWeight(0.7).acceptScore(0.8).acceptMargin(0.2).sampleFps(5.0));
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    @Test
    void profileResponsesUseDefaultMatchingAndUtcAuditTimestamps() {
        var profile = mock(RecognitionProfile.class);
        when(profile.getVersion()).thenReturn(7L);
        when(profile.getCreatedAt()).thenReturn(NOW);
        when(profile.getUpdatedAt()).thenReturn(NOW.plusSeconds(30));

        var model = mapper.toModel(profile);

        assertThat(model.getVersion()).isEqualTo(7L);
        assertThat(model.getCreatedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(model.getUpdatedAt()).isEqualTo(NOW.plusSeconds(30).atOffset(ZoneOffset.UTC));
        assertThat(model.getMatching()).isEqualTo(new com.processpuzzle.ai.model.MatchingSettings()
                .identifierWeight(MatchingSettings.DEFAULT_IDENTIFIER_WEIGHT)
                .acceptScore(MatchingSettings.DEFAULT_ACCEPT_SCORE)
                .acceptMargin(MatchingSettings.DEFAULT_ACCEPT_MARGIN)
                .sampleFps(MatchingSettings.DEFAULT_SAMPLE_FPS));
    }

    @Test
    void uploadResponsesPreserveTheKeySignedUrlHeadersAndExpiry() {
        var upload = new MediaUpload("my-org", MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 100, NOW);
        var headers = Map.of("Content-Type", "image/jpeg");

        var model = mapper.toModel(new MediaUploads.Slot(upload, "https://media/photo?signature=abc", headers));

        assertThat(model.getMediaKey()).isEqualTo(upload.getMediaKey().toString());
        assertThat(model.getUploadUrl()).hasToString("https://media/photo?signature=abc");
        assertThat(model.getRequiredHeaders()).isEqualTo(headers);
        assertThat(model.getExpiresAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(EnrollmentView.Status.class)
    void enrollmentResponsesPreserveEveryStatusAndTheirPhotos(EnrollmentView.Status status) {
        UUID objectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        var photo = new EnrollmentView.Photo(photoId, EnrollmentPhotoStatus.ENROLLED, "https://media/photo",
                "https://media/crop", "GER 1284", true, null, NOW);
        var view = new EnrollmentView("Boat", objectId, status, "GER 1234", "dinov2-small", List.of(photo), NOW);

        var model = mapper.toModel(view);

        assertThat(model.getEntityName()).isEqualTo("Boat");
        assertThat(model.getObjectId()).isEqualTo(objectId);
        assertThat(model.getStatus().name()).isEqualTo(status.name());
        assertThat(model.getIdentifierText()).isEqualTo("GER 1234");
        assertThat(model.getEmbeddingModel()).isEqualTo("dinov2-small");
        assertThat(model.getUpdatedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(model.getPhotos()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getPhotoId()).isEqualTo(photoId);
            assertThat(mapped.getStatus().name()).isEqualTo(photo.status().name());
            assertThat(mapped.getPhotoUrl()).hasToString("https://media/photo");
            assertThat(mapped.getCropUrl()).hasToString("https://media/crop");
            assertThat(mapped.getObservedIdentifierText()).isEqualTo("GER 1284");
            assertThat(mapped.getIdentifierMismatch()).isTrue();
            assertThat(mapped.getFailureReason()).isNull();
            assertThat(mapped.getAddedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        });
    }

    @ParameterizedTest
    @EnumSource(EnrollmentPhotoStatus.class)
    void photoResponsesPreserveStatusAndAbsentOptionalFields(EnrollmentPhotoStatus status) {
        var photo = new EnrollmentView.Photo(UUID.randomUUID(), status, null, null, null, false, "no crop", NOW);
        var view = new EnrollmentView("Boat", UUID.randomUUID(), EnrollmentView.Status.FAILED,
                null, null, List.of(photo), null);

        var model = mapper.toModel(view);

        assertThat(model.getUpdatedAt()).isNull();
        assertThat(model.getPhotos()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getStatus().name()).isEqualTo(status.name());
            assertThat(mapped.getPhotoUrl()).isNull();
            assertThat(mapped.getCropUrl()).isNull();
            assertThat(mapped.getObservedIdentifierText()).isNull();
            assertThat(mapped.getIdentifierMismatch()).isFalse();
            assertThat(mapped.getFailureReason()).isEqualTo("no crop");
        });
    }
}
