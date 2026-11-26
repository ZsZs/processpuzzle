package com.processpuzzle.ai.assist;

/** The model service failed to answer; the client's error is the cause and stays out of the message. */
public class AssistUnavailableException extends RuntimeException {

    public AssistUnavailableException(Throwable cause) {
        super("The model service could not answer", cause);
    }
}
