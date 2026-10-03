package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentsTest {

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
}
