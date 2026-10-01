package org.processpuzzle.ai.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Inbound web adapter. Talks only to the DesignSessionUseCase port,
 * so it knows nothing about Spring AI or any LLM vendor.
 */
@RestController
@RequestMapping("/api/ai/sessions")
class AssistantController {

  private final DesignSessionUseCase useCase;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  AssistantController(DesignSessionUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter chat(@PathVariable UUID sessionId, @RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(0L); // no timeout; the LLM call bounds the duration
    executor.submit(() -> {
      try {
        Consumer<AssistantEvent> send = event -> {
          try {
            emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
          } catch (IOException e) {
            throw new IllegalStateException("Client disconnected", e);
          }
        };
        useCase.chat(sessionId, request.message(), send);
        send.accept(new AssistantEvent.Done());
        emitter.complete();
      } catch (Exception e) {
        try {
          emitter.send(SseEmitter.event().data(new AssistantEvent.Error(e.getMessage()), MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
          // client is gone
        }
        emitter.completeWithError(e);
      }
    });
    return emitter;
  }

  @PutMapping("/{sessionId}/draft")
  List<ValidationIssue> replaceDraft(@PathVariable UUID sessionId, @RequestBody Map<ArtifactType, String> draft) {
    return useCase.replaceDraft(sessionId, draft);
  }

  @PostMapping("/{sessionId}/apply")
  ApplyResult apply(@PathVariable UUID sessionId) {
    return useCase.apply(sessionId);
  }

  record ChatRequest(String message) {}

  // ---- shared types (move to the module's api package) ----

  enum ArtifactType { ENTITY, STATE, WORKFLOW, APP }

  record ValidationIssue(ArtifactType artifactType, String path, String message, boolean blocking) {}

  record ApplyResult(boolean success, List<ValidationIssue> issues) {}

  record ClarificationOption(String id, String label) {}

  /** Serialized with a "type" discriminator matching the Angular AssistantEvent union. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = AssistantEvent.Token.class, name = "token"),
    @JsonSubTypes.Type(value = AssistantEvent.Clarification.class, name = "clarification"),
    @JsonSubTypes.Type(value = AssistantEvent.DraftUpdate.class, name = "draft_update"),
    @JsonSubTypes.Type(value = AssistantEvent.Issues.class, name = "issues"),
    @JsonSubTypes.Type(value = AssistantEvent.Explanation.class, name = "explanation"),
    @JsonSubTypes.Type(value = AssistantEvent.Done.class, name = "done"),
    @JsonSubTypes.Type(value = AssistantEvent.Error.class, name = "error")
  })
  sealed interface AssistantEvent {
    record Token(String text) implements AssistantEvent {}
    record Clarification(List<String> questions, List<ClarificationOption> options) implements AssistantEvent {}
    record DraftUpdate(ArtifactType artifactType, String yaml) implements AssistantEvent {}
    record Issues(List<ValidationIssue> issues) implements AssistantEvent {}
    record Explanation(String text) implements AssistantEvent {}
    record Done() implements AssistantEvent {}
    record Error(String message) implements AssistantEvent {}
  }

  /** Inbound port implemented by the application service. */
  interface DesignSessionUseCase {
    void chat(UUID sessionId, String userMessage, Consumer<AssistantEvent> events);
    List<ValidationIssue> replaceDraft(UUID sessionId, Map<ArtifactType, String> draft);
    ApplyResult apply(UUID sessionId);
  }
}
