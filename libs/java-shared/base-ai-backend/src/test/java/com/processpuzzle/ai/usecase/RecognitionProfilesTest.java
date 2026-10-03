package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        when(repository.save(any(RecognitionProfile.class))).thenAnswer(call -> call.getArgument(0));
        profiles = new RecognitionProfiles(repository, photos, provider, guard);
    }

    private static RecognitionProfiles.Draft draft(String attribute, String pattern, MatchingSettings matching) {
        return new RecognitionProfiles.Draft("Boat", "Sailboat", null, "boat", attribute, pattern, matching);
    }

    @Test
    void createsAProfileWithDefaultMatchingAndChecksDesignAccess() {
        RecognitionProfile created = profiles.create(ORG, draft("sailNumber", "^[A-Z]{3} ?[0-9]+$", null));

        assertThat(created.getEntityName()).isEqualTo("Boat");
        assertThat(created.getMatching().getAcceptScore()).isEqualTo(MatchingSettings.DEFAULT_ACCEPT_SCORE);
        verify(guard).requireDesign(ORG);
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
    void aProfileWithEnrolledSubjectsCannotBeDeleted() {
        when(repository.findByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(Optional.of(new RecognitionProfile(ORG, "Boat")));
        when(photos.existsByOrgKeyAndEntityName(ORG, "Boat")).thenReturn(true);

        assertThatThrownBy(() -> profiles.delete(ORG, "Boat"))
                .hasFieldOrPropertyWithValue("errorId", "ai.profile.has-enrollments");
    }
}
