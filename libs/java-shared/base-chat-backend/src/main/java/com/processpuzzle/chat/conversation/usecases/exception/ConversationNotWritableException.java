package com.processpuzzle.chat.conversation.usecases.exception;

/** The conversation is closed or archived, or its STATE_BOUND subject is not in a writable state. */
public class ConversationNotWritableException extends RuntimeException {

    public ConversationNotWritableException(String message) {
        super(message);
    }
}
