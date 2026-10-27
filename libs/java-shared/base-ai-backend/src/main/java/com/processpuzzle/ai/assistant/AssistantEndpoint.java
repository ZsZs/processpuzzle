package com.processpuzzle.ai.assistant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One designer turn over HTTP, streamed as server-sent events: {@code session} names the conversation the turn belongs
 * to, {@code delta} carries each piece of the answer as the model produces it, {@code reply} the whole answer once it
 * is complete, then {@code done}. A failure becomes an {@code error} event rather than a broken stream. A turn without
 * a session opens a new conversation.
 *
 * <p>The turn runs on the request thread, which is what lets the events go out as they happen: Spring MVC flushes an
 * emitter's events as they are sent once the handler has returned it, and buffers the ones sent before. Only exists
 * when the assistant is enabled.
 */
@RestController
@ConditionalOnProperty(prefix = "processpuzzle.ai.assistant", name = "enabled", havingValue = "true")
public class AssistantEndpoint {

    static final int MAX_MESSAGE_LENGTH = 4000;
    private static final Duration TURN_TIMEOUT = Duration.ofMinutes(3);

    private final DesignAssistant assistant;
    private final AssistantAccess access;

    public AssistantEndpoint(DesignAssistant assistant, AssistantAccess access) {
        this.assistant = assistant;
        this.access = access;
    }

    @PostMapping(value = "/organizations/{orgKey}/assistant/turns",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter turn(@PathVariable String orgKey, @RequestBody TurnRequest request) {
        if (!access.mayDesign() || !orgKey.equals(access.currentOrganization())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The caller may not design in this organization");
        }
        String message = request == null ? null : request.message();
        if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The message must be between 1 and %d characters".formatted(MAX_MESSAGE_LENGTH));
        }

        SseEmitter emitter = new SseEmitter(TURN_TIMEOUT.toMillis());
        try {
            UUID session = request.sessionId() == null ? assistant.openSession() : request.sessionId();
            send(emitter, "session", session.toString());
            String reply = assistant.respond(session, message, delta -> send(emitter, "delta", delta));
            send(emitter, "reply", reply);
            send(emitter, "done", "");
        } catch (AssistantQuotaExceededException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "The organization has used up its design assistant limit for today");
        } catch (AssistantSessionNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such design assistant session");
        } catch (UncheckedIOException e) {
            emitter.completeWithError(e.getCause());
            return emitter;
        } catch (RuntimeException e) {
            sendError(emitter);
        }
        emitter.complete();
        return emitter;
    }

    /** Sends one event; a client that went away surfaces as {@link UncheckedIOException}, which ends the turn. */
    private static void send(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendError(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("error").data("The assistant could not complete this turn."));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    /** The designer's message for one turn, and the conversation it continues; no conversation opens a new one. */
    public record TurnRequest(String message, UUID sessionId) {
    }
}
