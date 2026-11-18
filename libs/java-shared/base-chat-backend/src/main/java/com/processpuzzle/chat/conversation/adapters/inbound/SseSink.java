package com.processpuzzle.chat.conversation.adapters.inbound;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** A {@link StreamSink} over a Spring {@link SseEmitter}. */
final class SseSink implements StreamSink {

    private final SseEmitter emitter;

    SseSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String id, String event, Object data) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON);
        emitter.send(id == null ? builder : builder.id(id));
    }

    @Override
    public void comment(String text) throws IOException {
        emitter.send(SseEmitter.event().comment(text));
    }

    @Override
    public void complete() {
        emitter.complete();
    }
}
