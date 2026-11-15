package com.processpuzzle.chat.conversation.usecases.exception;

/**
 * The conversation, its definition or its subject is unknown — or the caller may not read it. One exception
 * for all of them: the answer must not reveal that a subject has a chat to somebody who may not see it.
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(String message) {
        super(message);
    }

    public static ConversationNotFoundException conversation() {
        return new ConversationNotFoundException("Conversation not found");
    }

    public static ConversationNotFoundException subject() {
        return new ConversationNotFoundException("Subject not found");
    }
}
