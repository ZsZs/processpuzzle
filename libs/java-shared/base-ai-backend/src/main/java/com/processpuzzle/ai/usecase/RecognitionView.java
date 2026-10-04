package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.domain.Recognition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A recognition as the use cases hand it out, the crop's signed URL included. */
public record RecognitionView(
        UUID recognitionId,
        String entityName,
        Recognition.Status status,
        Recognition.Outcome outcome,
        UUID objectId,
        Double score,
        String observedIdentifierText,
        Double observedIdentifierConfidence,
        String cropUrl,
        List<Recognition.RankedCandidate> candidates,
        List<UUID> candidatesWithoutGallery,
        String failureReason,
        Instant createdAt,
        Instant finishedAt) {
}
