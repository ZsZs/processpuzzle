package com.processpuzzle.ai.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One shot identified against a candidate list: the frames the caller uploaded, the subjects that may
 * be in them, and — once the vision server has answered — which one it is.
 *
 * <p>Deliberately transient. A recognition answers one question for its caller and is purged after a
 * retention period; it carries nothing about why the question was asked, and recording the answer is
 * the caller's business.
 */
@Entity
@Table(name = "ai_recognitions", indexes = @Index(columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recognition {

    public enum Status { QUEUED, DONE, FAILED }

    public enum Outcome { MATCHED, NEEDS_REVIEW, NO_SUBJECT }

    @Id
    @Column(name = "recognition_id")
    private UUID recognitionId;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_recognition_frames", joinColumns = @JoinColumn(name = "recognition_id"))
    @OrderColumn(name = "position")
    @Column(name = "media_key", nullable = false)
    private List<UUID> frameMediaKeys = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_recognition_candidate_ids", joinColumns = @JoinColumn(name = "recognition_id"))
    @OrderColumn(name = "position")
    @Column(name = "object_id", nullable = false)
    private List<UUID> candidateObjectIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_recognition_without_gallery", joinColumns = @JoinColumn(name = "recognition_id"))
    @OrderColumn(name = "position")
    @Column(name = "object_id", nullable = false)
    private List<UUID> candidatesWithoutGallery = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Outcome outcome;

    /** The recognized subject, when MATCHED. */
    @Column(name = "object_id")
    private UUID objectId;

    private Double score;

    @Column(name = "observed_identifier", length = 100)
    private String observedIdentifier;

    @Column(name = "observed_identifier_confidence")
    private Double observedIdentifierConfidence;

    @Column(name = "crop_object_name", length = 300)
    private String cropObjectName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_recognition_ranking", joinColumns = @JoinColumn(name = "recognition_id"))
    @OrderColumn(name = "rank")
    private List<RankedCandidate> ranking = new ArrayList<>();

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "vision_job_id", nullable = false)
    private UUID visionJobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Recognition(String orgKey, String entityName, List<UUID> frameMediaKeys, List<UUID> candidateObjectIds,
                       UUID visionJobId, Instant now) {
        this.recognitionId = UUID.randomUUID();
        this.orgKey = orgKey;
        this.entityName = entityName;
        this.status = Status.QUEUED;
        this.frameMediaKeys = new ArrayList<>(frameMediaKeys);
        this.candidateObjectIds = new ArrayList<>(candidateObjectIds);
        this.visionJobId = visionJobId;
        this.createdAt = now;
    }

    /** Which candidates had no enrolled photo when the job was built; they could only be matched by identifier. */
    public void withoutGallery(List<UUID> objectIds) {
        this.candidatesWithoutGallery = new ArrayList<>(objectIds);
    }

    public void noSubject(Instant now) {
        this.status = Status.DONE;
        this.outcome = Outcome.NO_SUBJECT;
        this.finishedAt = now;
    }

    /**
     * @param objectId the subject when the match is certain, null when a person has to choose
     */
    public void answered(UUID objectId, Double score, String observedIdentifier, Double observedIdentifierConfidence,
                         String cropObjectName, List<RankedCandidate> ranking, Instant now) {
        this.status = Status.DONE;
        this.outcome = objectId == null ? Outcome.NEEDS_REVIEW : Outcome.MATCHED;
        this.objectId = objectId;
        this.score = objectId == null ? null : score;
        this.observedIdentifier = observedIdentifier;
        this.observedIdentifierConfidence = observedIdentifierConfidence;
        this.cropObjectName = cropObjectName;
        this.ranking = new ArrayList<>(ranking);
        this.finishedAt = now;
    }

    public void failed(String reason, Instant now) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.finishedAt = now;
    }

    public boolean isPending() {
        return status == Status.QUEUED;
    }

    /** One candidate's scores, as ranked by the vision server. */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class RankedCandidate {

        @Column(name = "object_id", nullable = false)
        private UUID objectId;

        @Column(nullable = false)
        private double score;

        @Column(name = "identifier_score")
        private Double identifierScore;

        @Column(name = "embedding_score")
        private Double embeddingScore;
    }
}
