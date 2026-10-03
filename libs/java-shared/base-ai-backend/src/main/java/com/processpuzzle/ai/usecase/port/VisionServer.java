package com.processpuzzle.ai.usecase.port;

import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to the vision server — the shared infrastructure service that runs the models
 * (apps/vision-server, contract vision-server-api.yaml). Spoken in this module's own types; the
 * adapter maps them onto the generated client.
 *
 * <p>Every method may throw {@link VisionServerUnavailableException}. Callers treat that as "try
 * again later", never as an answer: a ticket that could not be submitted stays SUBMITTING and the
 * poller retries it.
 */
public interface VisionServer {

    /** Submits an enrollment job; idempotent per {@code jobId} on the server side. */
    void submitEnrollment(EnrollmentRequest request);

    /** The job's state, or empty if the server does not know it — expired, deleted, or lost in a restart. */
    Optional<JobState> job(UUID jobId);

    /** The result of an enrollment job that finished DONE. */
    EnrollmentOutcome enrollmentResult(UUID jobId);

    /** Discards a finished job's result, or cancels a running one. Unknown jobs are ignored. */
    void discard(UUID jobId);

    /**
     * @param identifierPattern null when the profile reads no identifier, which turns OCR off
     */
    record EnrollmentRequest(
            UUID jobId,
            String orgKey,
            String callbackToken,
            String detectorClass,
            boolean readIdentifier,
            String identifierPattern,
            List<Media> photos) {
    }

    record Media(String mediaId, String url) {
    }

    enum Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    record JobState(Status status, String failureReason) {
        public boolean isFinished() {
            return status == Status.DONE || status == Status.FAILED || status == Status.CANCELLED;
        }
    }

    record EnrollmentOutcome(List<PhotoOutcome> photos) {
    }

    /**
     * @param status ENROLLED, NO_SUBJECT, AMBIGUOUS or FAILED — never PENDING
     */
    record PhotoOutcome(
            String mediaId,
            EnrollmentPhotoStatus status,
            byte[] cropJpeg,
            String embeddingModel,
            byte[] embedding,
            String identifierText,
            String failureReason) {
    }
}
