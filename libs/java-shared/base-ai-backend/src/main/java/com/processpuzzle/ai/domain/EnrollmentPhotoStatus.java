package com.processpuzzle.ai.domain;

/** See ai-api.yaml EnrollmentPhotoStatus. Only ENROLLED photos contribute to the gallery. */
public enum EnrollmentPhotoStatus {
    PENDING,
    ENROLLED,
    NO_SUBJECT,
    AMBIGUOUS,
    FAILED
}
