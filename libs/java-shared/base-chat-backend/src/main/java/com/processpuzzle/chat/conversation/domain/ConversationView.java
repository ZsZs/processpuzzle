package com.processpuzzle.chat.conversation.domain;

/**
 * A conversation with what the caller may do in it and how far they have read it.
 *
 * @param lastReadSeq the caller's read marker; null when they do not follow the conversation
 */
public record ConversationView(Conversation conversation, ConversationAccess access, Long lastReadSeq) {

    /** A conversation the caller does not follow. */
    public ConversationView(Conversation conversation, ConversationAccess access) {
        this(conversation, access, null);
    }

    public boolean following() {
        return lastReadSeq != null;
    }

    /** Messages newer than the caller's marker; 0 when they do not follow it. */
    public long unread() {
        return following() ? Math.max(0, conversation.getLastSeq() - lastReadSeq) : 0;
    }
}
