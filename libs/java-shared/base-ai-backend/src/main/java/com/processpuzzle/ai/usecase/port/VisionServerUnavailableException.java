package com.processpuzzle.ai.usecase.port;

/** The vision server could not be reached, refused the call, or is not configured. Transient by assumption. */
public class VisionServerUnavailableException extends RuntimeException {

    public VisionServerUnavailableException(String message) {
        super(message);
    }

    public VisionServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
