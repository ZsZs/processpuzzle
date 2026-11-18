package com.processpuzzle.chat.conversation.events;

import com.processpuzzle.chat.conversation.domain.Message;
import java.util.UUID;

/**
 * A message was stored. Published inside the posting transaction and observed after it commits, so nobody is
 * told of a message that rolled back. In-process only: individual messages are too many for the durable event
 * registry, and a client that misses one recovers from history by its {@code seq}.
 */
public record MessagePostedEvent(String orgKey, UUID conversationId, Message message) {
}
