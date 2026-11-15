package com.processpuzzle.chat.conversation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What one live connection of one user may carry, decided when it opens.
 *
 * @param userSub       whose connection it is
 * @param conversations the followed conversations the user may read now — everything the stream pushes
 * @param expiresAt     when the connection must end: the token's expiry, but never later than the configured
 *                      maximum age. Nothing re-checks the token while the connection is open, so it ends and
 *                      the client reconnects with a fresh one — which re-authorizes
 * @param replay        the messages the client missed while disconnected, oldest first
 * @param resync        conversations in which the client missed too many to replay; it reloads their history
 */
public record StreamGrant(String userSub, Set<UUID> conversations, Instant expiresAt, List<Message> replay, Set<UUID> resync) {
}
