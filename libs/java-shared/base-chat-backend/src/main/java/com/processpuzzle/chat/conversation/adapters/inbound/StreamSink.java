package com.processpuzzle.chat.conversation.adapters.inbound;

import java.io.IOException;

/**
 * Where one connection's events go. An interface so the registry — which decides who receives what — can be
 * tested without a servlet container; {@link SseSink} is the real one.
 */
public interface StreamSink {

    /**
     * Sends one named event.
     *
     * @param id   the SSE event id, or null for none
     * @param data serialized as JSON
     * @throws IOException when the client is gone
     */
    void send(String id, String event, Object data) throws IOException;

    /** Sends a comment line, which a client ignores and a proxy counts as traffic. */
    void comment(String text) throws IOException;

    /** Ends the stream; the client sees it close and reconnects. */
    void complete();
}
