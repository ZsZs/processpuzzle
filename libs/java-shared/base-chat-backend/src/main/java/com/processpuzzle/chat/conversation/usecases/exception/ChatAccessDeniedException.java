package com.processpuzzle.chat.conversation.usecases.exception;

/** The caller may read the conversation but not do what they asked — post, or open one. */
public class ChatAccessDeniedException extends RuntimeException {

    public ChatAccessDeniedException(String message) {
        super(message);
    }
}
