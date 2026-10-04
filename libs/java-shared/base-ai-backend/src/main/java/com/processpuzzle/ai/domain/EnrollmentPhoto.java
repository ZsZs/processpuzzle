package com.processpuzzle.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One photo of a subject's gallery, and what the vision server made of it: the crop of the subject,
 * its embedding and the identifier text read on it.
 *
 * <p>The photo itself is not this module's. It is an artifact of the subject, held in the attribute
 * the profile's {@code galleryAttributeKey} names; {@code photoRef} says which one, opaquely, as
 * {@code SubjectDirectory} reported it. Only the crop is stored here.
 *
 * <p>The embedding lives on the photo rather than in a table of its own because it is 1:1 with the
 * crop, and a re-embedding with a newer model replaces it in place. {@code embeddingModel} records
 * which model produced it; vectors of different models are not comparable.
 */
@Entity
@Table(name = "ai_enrollment_photos",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "entity_name", "object_id", "photo_ref"}),
        indexes = @Index(columnList = "org_key, entity_name, object_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentPhoto {

    /** Large enough for a 1024-dimension float32 vector, DINOv2-large's, with room to spare. */
    private static final int MAX_EMBEDDING_BYTES = 16384;

    @Id
    @Column(name = "photo_id")
    private UUID photoId;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    @Column(name = "object_id", nullable = false)
    private UUID objectId;

    @Column(name = "photo_ref", nullable = false, length = 500)
    private String photoRef;

    @Column(name = "crop_object_name", length = 300)
    private String cropObjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentPhotoStatus status;

    @Column(name = "observed_identifier", length = 100)
    private String observedIdentifier;

    @Column(name = "embedding_model", length = 200)
    private String embeddingModel;

    @Column(name = "embedding", length = MAX_EMBEDDING_BYTES)
    private byte[] embedding;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** The vision job processing this photo, while it is PENDING. */
    @Column(name = "vision_job_id")
    private UUID visionJobId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public EnrollmentPhoto(String orgKey, String entityName, UUID objectId, String photoRef, Instant now) {
        this.photoId = UUID.randomUUID();
        this.orgKey = orgKey;
        this.entityName = entityName;
        this.objectId = objectId;
        this.photoRef = photoRef;
        this.status = EnrollmentPhotoStatus.PENDING;
        this.addedAt = now;
    }

    public void assignTo(UUID visionJobId) {
        this.visionJobId = visionJobId;
    }

    public void enrolled(String cropObjectName, String embeddingModel, byte[] embedding, String observedIdentifier) {
        this.status = EnrollmentPhotoStatus.ENROLLED;
        this.cropObjectName = cropObjectName;
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
        this.observedIdentifier = observedIdentifier;
        this.failureReason = null;
        this.visionJobId = null;
    }

    /** NO_SUBJECT, AMBIGUOUS or FAILED: nothing usable came out of the photo. */
    public void rejected(EnrollmentPhotoStatus outcome, String reason) {
        if (outcome == EnrollmentPhotoStatus.ENROLLED || outcome == EnrollmentPhotoStatus.PENDING) {
            throw new IllegalArgumentException("not a rejection: " + outcome);
        }
        this.status = outcome;
        this.failureReason = reason;
        this.visionJobId = null;
    }

    /** A re-embedding with the current model, from the stored crop. */
    public void reembedded(String embeddingModel, byte[] embedding) {
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
    }
}
