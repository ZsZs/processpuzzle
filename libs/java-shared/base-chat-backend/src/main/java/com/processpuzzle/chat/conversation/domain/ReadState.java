package com.processpuzzle.chat.conversation.domain;

import java.util.UUID;

/**
 * Where a user stands in a conversation after marking it read.
 *
 * @param lastReadSeq their marker
 * @param lastSeq     the newest message of the conversation
 */
public record ReadState(UUID conversationId, long lastReadSeq, long lastSeq) {

    public long unread() {
        return Math.max(0, lastSeq - lastReadSeq);
    }
}
