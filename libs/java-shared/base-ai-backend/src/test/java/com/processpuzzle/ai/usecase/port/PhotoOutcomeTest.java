package com.processpuzzle.ai.usecase.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.usecase.port.VisionServer.PhotoOutcome;
import org.junit.jupiter.api.Test;

class PhotoOutcomeTest {

    @Test
    void equalArrayContentsGiveEqualOutcomesAndHashCodes() {
        PhotoOutcome first = enrolled(new byte[] {1, 2}, new byte[] {3, 4});
        PhotoOutcome second = enrolled(new byte[] {1, 2}, new byte[] {3, 4});

        assertThat(first).isEqualTo(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(second).isEqualTo(first);
        assertThat(first).hasToString("PhotoOutcome[mediaId=p1, status=ENROLLED, cropJpeg=[1, 2], "
                + "embeddingModel=dinov2-small, embedding=[3, 4], identifierText=GER 1234, failureReason=null]");
    }

    @Test
    void differentArrayContentsAreNotEqual() {
        PhotoOutcome outcome = enrolled(new byte[] {1}, new byte[] {2});

        assertThat(outcome)
                .isNotEqualTo(enrolled(new byte[] {9}, new byte[] {2}))
                .isNotEqualTo(enrolled(new byte[] {1}, new byte[] {9}))
                .isNotEqualTo(enrolled(null, new byte[] {2}))
                .isNotEqualTo(enrolled(new byte[] {1}, null))
                .isNotEqualTo(null)
                .isNotEqualTo("not a photo outcome");
    }

    @Test
    void allScalarFieldsParticipateInEquality() {
        PhotoOutcome outcome = enrolled(new byte[] {1}, new byte[] {2});

        assertThat(outcome)
                .isNotEqualTo(new PhotoOutcome("p2", outcome.status(), outcome.cropJpeg(), outcome.embeddingModel(),
                        outcome.embedding(), outcome.identifierText(), outcome.failureReason()))
                .isNotEqualTo(new PhotoOutcome(outcome.mediaId(), EnrollmentPhotoStatus.FAILED, outcome.cropJpeg(),
                        outcome.embeddingModel(), outcome.embedding(), outcome.identifierText(), outcome.failureReason()))
                .isNotEqualTo(new PhotoOutcome(outcome.mediaId(), outcome.status(), outcome.cropJpeg(), "other-model",
                        outcome.embedding(), outcome.identifierText(), outcome.failureReason()))
                .isNotEqualTo(new PhotoOutcome(outcome.mediaId(), outcome.status(), outcome.cropJpeg(), outcome.embeddingModel(),
                        outcome.embedding(), "GER 5678", outcome.failureReason()))
                .isNotEqualTo(new PhotoOutcome(outcome.mediaId(), outcome.status(), outcome.cropJpeg(), outcome.embeddingModel(),
                        outcome.embedding(), outcome.identifierText(), "failed"));
    }

    @Test
    void rejectedOutcomesSupportAbsentArraysAndMetadata() {
        PhotoOutcome first = new PhotoOutcome("p1", EnrollmentPhotoStatus.NO_SUBJECT, null, null, null, null, "no boat");
        PhotoOutcome second = new PhotoOutcome("p1", EnrollmentPhotoStatus.NO_SUBJECT, null, null, null, null, "no boat");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).hasToString("PhotoOutcome[mediaId=p1, status=NO_SUBJECT, cropJpeg=null, "
                + "embeddingModel=null, embedding=null, identifierText=null, failureReason=no boat]");
    }

    private static PhotoOutcome enrolled(byte[] crop, byte[] embedding) {
        return new PhotoOutcome("p1", EnrollmentPhotoStatus.ENROLLED, crop, "dinov2-small", embedding, "GER 1234", null);
    }
}
