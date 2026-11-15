package com.processpuzzle.chat.conversation.domain;

/**
 * The outcome of a post.
 *
 * @param message the stored message
 * @param created false when the post was a replay and {@code message} is the one stored earlier
 */
public record PostedMessage(Message message, boolean created) {
}
