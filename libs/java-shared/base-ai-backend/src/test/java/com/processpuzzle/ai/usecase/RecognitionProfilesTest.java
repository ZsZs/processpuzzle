package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

class RecognitionProfilesTest {

    private static final String ORG = "my-org";

    private RecognitionProfileRepository repository;
    private EnrollmentPhotoRepository photos;
    private SubjectDirectory subjects;
    private OrganizationGuard guard;
    private RecognitionProfiles profiles;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RecognitionProfileRepository.class);
        photos = mock(EnrollmentPhotoRepository.class);
        subjects = mock(SubjectDirectory.class);
        guard = mock(OrganizationGuard.class);
        ObjectProvider<SubjectDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(subjects);
        when(subjects.entityTypeExists(ORG, "Boat")).thenReturn(true);
        when(subjects.isTextAttribute(ORG, "Boat", "sailNumber")).thenReturn(true);
        when(subjects.isPhotoAttribute(ORG, "Boat", "photos")).thenReturn(true);
        when(repository.save(any(RecognitionProfile.class))).thenAnswer(call -> call.getArgument(0));
        profiles = new RecognitionProfiles(repository, photos, provider, guard);
    }

    private static RecognitionProfiles.Draft draft(String attribute, String pattern, MatchingSettings matching) {
        return new RecognitionProfiles.Draft("Boat", "Sailboat", null, "boat", "photos", attribute, pattern, matching);
    }

    @Test
    void createsAProfileWithDefaultMatchingAndChecksDesignAccess() {
        RecognitionProfile created = profiles.create(ORG, draft("sailNumber", "^[A-Z]{3} ?[0-9]+$", null));

        assertThat(created.getEntityName()).isEqualTo("Boat");
        assertThat(created.getGalleryAttributeKey()).isEqualTo("photos");
        assertThat(created.getMatching().getAcceptScore()).isEqualTo(MatchingSettings.DEFAULT_ACCEPT_SCORE);
        verify(guard).requireDesign(ORG);
        verify(subjects).isPhotoAttribute(ORG, "Boat", "photos");
    }

    @Test
    void theGalleryAttributeMustBeAnArtifactAttribute() {
        RecognitionProfiles.Draft notPhotos = new RecognitionProfiles.Draft("Boat", "Sailboat", null, "boat", "sailNumber",
                null, null, null);

        assertThatThrownBy(() -> profiles.create(ORG, notPhotos))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.gallery-attribute-invalid");
        when(repository.findByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(Optional.of(new RecognitionProfile(ORG, "Boat")));
        assertThatThrownBy(() -> profiles.update(ORG, "Boat", notPhotos))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.gallery-attribute-invalid");
        verify(repository, never()).save(any());
    }

    @Test
    void anUpdateReplacesTheGalleryAttribute() {
        RecognitionProfile existing = new RecognitionProfile(ORG, "Boat");
        existing.setGalleryAttributeKey("oldPhotos");
        when(repository.findByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(Optional.of(existing));

        RecognitionProfile updated = profiles.update(ORG, "Boat", draft(null, null, null));

        assertThat(updated.getGalleryAttributeKey()).isEqualTo("photos");
    }

    @Test
    void aSecondProfileForTheSameEntityIsAConflict() {
        when(repository.existsByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(true);

        assertThatThrownBy(() -> profiles.create(ORG, draft(null, null, null)))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.CONFLICT));
    }

    @Test
    void rejectsAnUnknownEntityTypeANonTextAttributeAndABrokenPattern() {
        when(subjects.entityTypeExists(ORG, "Boat")).thenReturn(false);
        assertThatThrownBy(() -> profiles.create(ORG, draft(null, null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.entity-not-found");

        when(subjects.entityTypeExists(ORG, "Boat")).thenReturn(true);
        assertThatThrownBy(() -> profiles.create(ORG, draft("length", null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.identifier-attribute-invalid");

        assertThatThrownBy(() -> profiles.create(ORG, draft(null, "[A-Z", null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.identifier-pattern-invalid");
    }

    @Test
    void rejectsMatchingSettingsOutOfRange() {
        assertThatThrownBy(() -> profiles.create(ORG, draft(null, null, new MatchingSettings(1.5, 0.75, 0.1, 3))))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid");
        assertThatThrownBy(() -> profiles.create(ORG, draft(null, null, new MatchingSettings(0.6, 0.75, 0.1, 60))))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid");
    }

    @Test
    void seedingIsCreateOnlyAndDoesNotAskBaseEntity() {
        when(repository.existsByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(false, true);
        when(subjects.entityTypeExists(ORG, "Boat")).thenReturn(false);

        assertThat(profiles.seed(ORG, draft("sailNumber", "^[A-Z]{3}$", null))).isPresent();
        assertThat(profiles.seed(ORG, draft("sailNumber", "^[A-Z]{3}$", null))).isEmpty();
        assertThatThrownBy(() -> profiles.seed(ORG, draft(null, "[A-Z", null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.identifier-pattern-invalid");
        verify(guard, never()).requireDesign(ORG);
        verifyNoInteractions(subjects);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void seedingRejectsMissingRequiredFieldsBeforeAccessingTheRepository(String missing) {
        assertThatThrownBy(() -> profiles.seed(ORG, new RecognitionProfiles.Draft(missing, "Sailboat", null, "boat", "photos", null, null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid")
                .hasMessage("entityName is required.");
        assertThatThrownBy(() -> profiles.seed(ORG, new RecognitionProfiles.Draft("Boat", missing, null, "boat", "photos", null, null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid")
                .hasMessage("name is required.");
        assertThatThrownBy(() -> profiles.seed(ORG, new RecognitionProfiles.Draft("Boat", "Sailboat", null, missing, "photos", null, null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid")
                .hasMessage("detectorClass is required.");
        assertThatThrownBy(() -> profiles.seed(ORG, new RecognitionProfiles.Draft("Boat", "Sailboat", null, "boat", missing, null, null, null)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid")
                .hasMessage("galleryAttributeKey is required.");
        verifyNoInteractions(repository, subjects, guard);
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1, 0.75, 0.1, 3, identifierWeight",
            "1.1, 0.75, 0.1, 3, identifierWeight",
            "0.6, -0.1, 0.1, 3, acceptScore",
            "0.6, 1.1, 0.1, 3, acceptScore",
            "0.6, 0.75, -0.1, 3, acceptMargin",
            "0.6, 0.75, 1.1, 3, acceptMargin",
            "0.6, 0.75, 0.1, 0.49, sampleFps",
            "0.6, 0.75, 0.1, 30.1, sampleFps"
    })
    void seedingRejectsOutOfRangeMatchingSettings(double weight, double score, double margin, double fps, String field) {
        MatchingSettings matching = new MatchingSettings(weight, score, margin, fps);

        assertThatThrownBy(() -> profiles.seed(ORG, draft(null, null, matching)))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.invalid")
                .hasMessageContaining(field);
        verifyNoInteractions(repository, subjects, guard);
    }

    @ParameterizedTest
    @CsvSource({"0, 0, 0, 0.5", "1, 1, 1, 30"})
    void seedingAcceptsMatchingBoundaryValuesAndPreservesTheWholeDraft(double weight, double score, double margin, double fps) {
        MatchingSettings matching = new MatchingSettings(weight, score, margin, fps);
        RecognitionProfiles.Draft input = new RecognitionProfiles.Draft(
                "Boat", "Sailboat", "Identify sailing boats", "boat", "photos", "sailNumber", "^[A-Z]{3}$", matching);

        RecognitionProfile created = profiles.seed(ORG, input).orElseThrow();

        assertThat(created.getOrgKey()).isEqualTo(ORG);
        assertThat(created.getEntityName()).isEqualTo("Boat");
        assertThat(created.getName()).isEqualTo("Sailboat");
        assertThat(created.getDescription()).isEqualTo("Identify sailing boats");
        assertThat(created.getDetectorClass()).isEqualTo("boat");
        assertThat(created.getGalleryAttributeKey()).isEqualTo("photos");
        assertThat(created.getIdentifierAttributeKey()).isEqualTo("sailNumber");
        assertThat(created.getIdentifierPattern()).isEqualTo("^[A-Z]{3}$");
        assertThat(created.getMatching()).isSameAs(matching);
        verify(repository).save(created);
        verifyNoInteractions(subjects, guard);
    }

    @Test
    void seedingDefaultsMatchingAndClearsBlankOptionalIdentifierFields() {
        RecognitionProfile created = profiles.seed(ORG, draft("  ", "\t", null)).orElseThrow();

        assertThat(created.getIdentifierAttributeKey()).isNull();
        assertThat(created.getIdentifierPattern()).isNull();
        assertThat(created.getMatching()).usingRecursiveComparison().isEqualTo(MatchingSettings.defaults());
        verifyNoInteractions(subjects, guard);
    }

    @Test
    void anExistingSeedIsNeverSavedOrLoadedForModification() {
        when(repository.existsByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(true);

        assertThat(profiles.seed(ORG, draft(null, null, null))).isEmpty();

        verify(repository, never()).save(any());
        verify(repository, never()).findByOrgKeyAndEntityName(ORG, "Boat");
        verifyNoInteractions(subjects, guard);
    }

    @Test
    void aProfileWithEnrolledSubjectsCannotBeDeleted() {
        when(repository.findByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(Optional.of(new RecognitionProfile(ORG, "Boat")));
        when(photos.existsByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(true);

        assertThatThrownBy(() -> profiles.delete(ORG, "Boat"))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.has-enrollments");
    }
}
