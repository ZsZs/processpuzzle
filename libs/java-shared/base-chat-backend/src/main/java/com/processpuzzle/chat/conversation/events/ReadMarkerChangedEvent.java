package com.processpuzzle.chat.conversation.events;

import java.util.UUID;

/**
 * A user followed a conversation, moved their read marker, or stopped following it. Observed after commit by
 * the user's open streams, which add or drop the conversation and tell the user's other sessions where the
 * marker now stands.
 *
 * @param lastReadSeq where the marker stands; meaningless when {@code following} is false
 * @param following   false when the user unfollowed
 */
public record ReadMarkerChangedEvent(String orgKey, String userSub, UUID conversationId, long lastReadSeq, boolean following) {
}
