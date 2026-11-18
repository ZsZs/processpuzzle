package com.processpuzzle.chat.conversation.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of {@link Message}: its number within its conversation. A plain mutable class
 * rather than a record, because an {@code @IdClass} needs a public no-arg constructor.
 */
public class MessageKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID conversationId;
    private long seq;

    public MessageKey() {
        // required by JPA
    }

    public MessageKey(UUID conversationId, long seq) {
        this.conversationId = conversationId;
        this.seq = seq;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageKey that)) {
            return false;
        }
        return seq == that.seq && Objects.equals(conversationId, that.conversationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, seq);
    }

    @Override
    public String toString() {
        return conversationId + "#" + seq;
    }
}
