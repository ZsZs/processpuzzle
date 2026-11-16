package com.processpuzzle.chat.conversation.usecases.exception;

import java.time.Duration;

/** A user posted faster than the rate limit allows. Surfaced as 429 with {@code Retry-After}. */
public class ChatRateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public ChatRateLimitedException(Duration retryAfter) {
        super("You are posting too fast. Try again in a moment.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
