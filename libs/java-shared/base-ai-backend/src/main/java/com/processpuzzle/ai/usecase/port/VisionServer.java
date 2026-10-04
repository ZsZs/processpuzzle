package com.processpuzzle.ai.usecase.port;

import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to the vision server — the shared infrastructure service that runs the models
 * (apps/vision-server, contract vision-server-api.yaml). Spoken in this module's own types; the
 * adapter maps them onto the generated client.
 *
 * <p>Every method may throw {@link VisionServerUnavailableException}. Callers treat that as "try
 * again later", never as an answer: a ticket that could not be submitted stays SUBMITTING and the
 * poller retries it. A submission the server rejects for what it is throws
 * {@link VisionJobRefusedException} instead, which retrying would not change.
 */
public interface VisionServer {

    /** Submits an enrollment job; idempotent per {@code jobId} on the server side. */
    void submitEnrollment(EnrollmentRequest request);

    /** Submits a recognition job; idempotent per {@code jobId} on the server side. */
    void submitRecognition(RecognitionRequest request);

    /** The job's state, or empty if the server does not know it — expired, deleted, or lost in a restart. */
    Optional<JobState> job(UUID jobId);

    /** The result of an enrollment job that finished DONE. */
    EnrollmentOutcome enrollmentResult(UUID jobId);

    /** The result of a recognition job that finished DONE. */
    RecognitionOutcome recognitionResult(UUID jobId);

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

    /**
     * Frames of one subject, matched against the candidates.
     *
     * @param identifierPattern null when the profile reads no identifier, which turns OCR off
     */
    record RecognitionRequest(
            UUID jobId,
            String orgKey,
            String callbackToken,
            String detectorClass,
            boolean readIdentifier,
            String identifierPattern,
            double identifierWeight,
            double acceptScore,
            double acceptMargin,
            List<Media> frames,
            List<Candidate> candidates) {
    }

    /**
     * @param identifierText null when the subject has none registered
     * @param gallery        embeddings, each tagged with its model; empty means identifier only
     */
    record Candidate(String candidateId, String identifierText, List<GalleryEmbedding> gallery) {
    }

    record GalleryEmbedding(String model, byte[] vector) {

        @Override
        public boolean equals(Object other) {
            return other instanceof GalleryEmbedding that && Objects.equals(model, that.model) && Arrays.equals(vector, that.vector);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(model) + Arrays.hashCode(vector);
        }

        @Override
        public String toString() {
            return "GalleryEmbedding[model=" + model + ", vector=" + vector.length + " bytes]";
        }
    }

    /** The subject seen in the frames, if any; empty when there was none. */
    record RecognitionOutcome(Optional<SubjectSighting> subject) {
    }

    /**
     * @param candidateId the certain match, null when a person has to choose
     * @param ranking     the top candidates, best first
     */
    record SubjectSighting(
            String candidateId,
            Double score,
            String identifierText,
            Double identifierConfidence,
            byte[] cropJpeg,
            List<CandidateScore> ranking) {

        @Override
        public boolean equals(Object other) {
            return other instanceof SubjectSighting that
                    && Objects.equals(candidateId, that.candidateId)
                    && Objects.equals(score, that.score)
                    && Objects.equals(identifierText, that.identifierText)
                    && Objects.equals(identifierConfidence, that.identifierConfidence)
                    && Arrays.equals(cropJpeg, that.cropJpeg)
                    && Objects.equals(ranking, that.ranking);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(candidateId, score, identifierText, identifierConfidence, ranking);
            return 31 * result + Arrays.hashCode(cropJpeg);
        }

        @Override
        public String toString() {
            return "SubjectSighting[candidateId=" + candidateId + ", score=" + score + ", identifierText=" + identifierText
                    + ", identifierConfidence=" + identifierConfidence + ", cropJpeg="
                    + (cropJpeg == null ? "null" : cropJpeg.length + " bytes") + ", ranking=" + ranking + "]";
        }
    }

    record CandidateScore(String candidateId, double score, Double identifierScore, Double embeddingScore) {
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

        @Override
        public boolean equals(Object other) {
            return other instanceof PhotoOutcome that
                    && Objects.equals(mediaId, that.mediaId)
                    && status == that.status
                    && Arrays.equals(cropJpeg, that.cropJpeg)
                    && Objects.equals(embeddingModel, that.embeddingModel)
                    && Arrays.equals(embedding, that.embedding)
                    && Objects.equals(identifierText, that.identifierText)
                    && Objects.equals(failureReason, that.failureReason);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(mediaId, status, embeddingModel, identifierText, failureReason);
            result = 31 * result + Arrays.hashCode(cropJpeg);
            return 31 * result + Arrays.hashCode(embedding);
        }

        @Override
        public String toString() {
            return "PhotoOutcome[mediaId=" + mediaId + ", status=" + status
                    + ", cropJpeg=" + Arrays.toString(cropJpeg) + ", embeddingModel=" + embeddingModel
                    + ", embedding=" + Arrays.toString(embedding) + ", identifierText=" + identifierText
                    + ", failureReason=" + failureReason + "]";
        }
    }
}
