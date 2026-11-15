package com.processpuzzle.chat.conversation.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of {@link ReadMarker}: one marker per user and conversation. A plain mutable class
 * rather than a record, because an {@code @IdClass} needs a public no-arg constructor.
 */
public class ReadMarkerKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID conversationId;
    private String userSub;

    public ReadMarkerKey() {
        // required by JPA
    }

    public ReadMarkerKey(UUID conversationId, String userSub) {
        this.conversationId = conversationId;
        this.userSub = userSub;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserSub() {
        return userSub;
    }

    public void setUserSub(String userSub) {
        this.userSub = userSub;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReadMarkerKey that)) {
            return false;
        }
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(userSub, that.userSub);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, userSub);
    }

    @Override
    public String toString() {
        return userSub + "@" + conversationId;
    }
}
