package com.processpuzzle.orgadmin.usecases.inbound.exception;

import java.time.Duration;

/** Too many registration attempts from one client address. Surfaced as 429. */
public class RegistrationRateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public RegistrationRateLimitedException(Duration retryAfter) {
        super("Too many registration attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
