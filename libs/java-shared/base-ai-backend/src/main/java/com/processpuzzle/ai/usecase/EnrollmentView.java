package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A subject's enrollment as the use cases hand it out, signed URLs included. */
public record EnrollmentView(
        String entityName,
        UUID objectId,
        Status status,
        String identifierText,
        String embeddingModel,
        List<Photo> photos,
        Instant updatedAt) {

    public enum Status { NOT_ENROLLED, PROCESSING, READY, FAILED }

    public record Photo(
            UUID photoId,
            EnrollmentPhotoStatus status,
            String photoUrl,
            String cropUrl,
            String observedIdentifierText,
            boolean identifierMismatch,
            String failureReason,
            Instant addedAt) {
    }
}
