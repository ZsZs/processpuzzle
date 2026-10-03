package com.processpuzzle.ai.usecase.port;

import java.util.Optional;
import java.util.UUID;

/** The vision server when none is configured: every call throws {@link VisionServerUnavailableException}. */
public class UnavailableVisionServer implements VisionServer {

    @Override
    public void submitEnrollment(EnrollmentRequest request) {
        throw unavailable();
    }

    @Override
    public Optional<JobState> job(UUID jobId) {
        throw unavailable();
    }

    @Override
    public EnrollmentOutcome enrollmentResult(UUID jobId) {
        throw unavailable();
    }

    @Override
    public void discard(UUID jobId) {
        throw unavailable();
    }

    private static VisionServerUnavailableException unavailable() {
        return new VisionServerUnavailableException("No vision server is configured (processpuzzle.ai.vision-server.base-url).");
    }
}
