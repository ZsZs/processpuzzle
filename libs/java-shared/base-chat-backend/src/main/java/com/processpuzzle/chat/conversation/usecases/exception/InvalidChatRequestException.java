package com.processpuzzle.chat.conversation.usecases.exception;

/** The request breaks a rule of its own — an empty or too long message, a missing title or client id. */
public class InvalidChatRequestException extends RuntimeException {

    public InvalidChatRequestException(String message) {
        super(message);
    }
}
