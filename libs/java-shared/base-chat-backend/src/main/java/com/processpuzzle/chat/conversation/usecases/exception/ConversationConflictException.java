package com.processpuzzle.chat.conversation.usecases.exception;

/**
 * A conversation cannot be opened: a SINGLE definition's one conversation exists, or the definition's
 * lifecycle does not let anybody open one by hand.
 */
public class ConversationConflictException extends RuntimeException {

    public ConversationConflictException(String message) {
        super(message);
    }
}
