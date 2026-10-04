package com.processpuzzle.ai.usecase.port;

/**
 * The vision server rejected a submission for what it contains — an invalid request, or a gallery of
 * another embedding model. Unlike {@link VisionServerUnavailableException}, resubmitting the same job
 * would be rejected again, so the job is failed at once.
 */
public class VisionJobRefusedException extends RuntimeException {

    public VisionJobRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
