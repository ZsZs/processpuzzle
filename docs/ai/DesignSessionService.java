package org.processpuzzle.ai.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import org.processpuzzle.ai.adapter.in.web.AssistantController.ApplyResult;
import org.processpuzzle.ai.adapter.in.web.AssistantController.ArtifactType;
import org.processpuzzle.ai.adapter.in.web.AssistantController.AssistantEvent;
import org.processpuzzle.ai.adapter.in.web.AssistantController.ClarificationOption;
import org.processpuzzle.ai.adapter.in.web.AssistantController.DesignSessionUseCase;
import org.processpuzzle.ai.adapter.in.web.AssistantController.ValidationIssue;

/**
 * Application service. Uses Spring AI's ChatClient (the LLM adapter) and exposes
 * tools to the model. The model never writes to the customer's app: every tool call
 * only changes the session draft, which the customer reviews and applies explicitly.
 *
 * NOTE: replace the in-memory session map with a repository (tenant-scoped, persisted).
 */
@Service
class DesignSessionService implements DesignSessionUseCase {

  private static final String SYSTEM_PROMPT = """
      You are a ProcessPuzzle workflow designer. You help business users turn a plain-language
      description of their process into Entity, State, Workflow and App definitions.

      Rules:
      - If the description is ambiguous, call ask_clarification (max 5 questions) BEFORE proposing anything.
      - Build in order: entities, states, workflow, app. Reference earlier artifacts by ID only.
      - Use only the fields allowed by the tool schemas. Never invent fields.
      - After each proposal call validate_draft and fix reported issues yourself.
      - Finish every turn with explain_change: a short, non-technical summary of what changed.
      - Answer in the language of the user.
      """;

  private final ChatClient chatClient;
  private final DefinitionValidator validator;
  private final DefinitionImporter importer;
  private final Map<UUID, Draft> sessions = new ConcurrentHashMap<>();

  DesignSessionService(ChatClient.Builder builder, DefinitionValidator validator, DefinitionImporter importer) {
    this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    this.validator = validator;
    this.importer = importer;
  }

  @Override
  public void chat(UUID sessionId, String userMessage, Consumer<AssistantEvent> events) {
    Draft draft = sessions.computeIfAbsent(sessionId, id -> new Draft());
    DefinitionTools tools = new DefinitionTools(draft, validator, events);

    chatClient.prompt()
        .user(userMessage)
        .tools(tools)
        .stream()
        .content()
        .doOnNext(text -> events.accept(new AssistantEvent.Token(text)))
        .blockLast(); // runs on a virtual thread, see controller
  }

  @Override
  public List<ValidationIssue> replaceDraft(UUID sessionId, Map<ArtifactType, String> yaml) {
    Draft draft = sessions.computeIfAbsent(sessionId, id -> new Draft());
    draft.artifacts.clear();
    draft.artifacts.putAll(yaml);
    return validator.validate(draft.artifacts);
  }

  @Override
  public ApplyResult apply(UUID sessionId) {
    Draft draft = sessions.get(sessionId);
    if (draft == null) return new ApplyResult(false, List.of());
    List<ValidationIssue> issues = validator.validate(draft.artifacts);
    if (issues.stream().anyMatch(ValidationIssue::blocking)) return new ApplyResult(false, issues);
    importer.importDefinitions(draft.artifacts); // existing YAML import path of the platform
    return new ApplyResult(true, issues);
  }

  // ---- ports implemented by existing platform modules ----

  interface DefinitionValidator {
    List<ValidationIssue> validate(Map<ArtifactType, String> artifacts);
  }

  interface DefinitionImporter {
    void importDefinitions(Map<ArtifactType, String> artifacts);
  }

  static class Draft {
    final Map<ArtifactType, String> artifacts = new EnumMap<>(ArtifactType.class);
  }

  /**
   * Tools the LLM may call. One instance per request, bound to the session draft.
   * Arguments arrive as YAML strings; the validator checks them against the platform's
   * JSON Schemas (use typed records instead of strings once the schemas are mapped).
   */
  static class DefinitionTools {
    private final Draft draft;
    private final DefinitionValidator validator;
    private final Consumer<AssistantEvent> events;

    DefinitionTools(Draft draft, DefinitionValidator validator, Consumer<AssistantEvent> events) {
      this.draft = draft;
      this.validator = validator;
      this.events = events;
    }

    @Tool(description = "Ask the user up to 5 clarifying questions, with suggested answers, before designing.")
    String ask_clarification(
        @ToolParam(description = "The questions, in the user's language") List<String> questions,
        @ToolParam(description = "Suggested short answers the user can click") List<String> suggestions) {
      List<ClarificationOption> options = suggestions.stream()
          .map(s -> new ClarificationOption(UUID.randomUUID().toString(), s))
          .toList();
      events.accept(new AssistantEvent.Clarification(questions, options));
      return "Questions shown to the user. Stop and wait for the answer.";
    }

    @Tool(description = "Create or replace the ENTITY definitions (YAML) of the draft.")
    String propose_entities(@ToolParam(description = "Entity definitions as YAML") String yaml) {
      return store(ArtifactType.ENTITY, yaml);
    }

    @Tool(description = "Create or replace the STATE machine definitions (YAML). Reference entities by ID.")
    String propose_states(@ToolParam(description = "State machine definitions as YAML") String yaml) {
      return store(ArtifactType.STATE, yaml);
    }

    @Tool(description = "Create or replace the WORKFLOW definition (YAML). Reference entities and states by ID.")
    String propose_workflow(@ToolParam(description = "Workflow definition as YAML") String yaml) {
      return store(ArtifactType.WORKFLOW, yaml);
    }

    @Tool(description = "Create or replace the APP definition (pages, routes, widgets) as YAML.")
    String propose_app(@ToolParam(description = "App definition as YAML") String yaml) {
      return store(ArtifactType.APP, yaml);
    }

    @Tool(description = "Validate the current draft. Returns the list of issues; fix blocking ones.")
    String validate_draft() {
      List<ValidationIssue> issues = validator.validate(draft.artifacts);
      events.accept(new AssistantEvent.Issues(issues));
      return issues.isEmpty()
          ? "No issues."
          : issues.stream().map(i -> i.artifactType() + " " + i.path() + ": " + i.message()).toList().toString();
    }

    @Tool(description = "Show the user a short, non-technical summary of what changed in this turn.")
    String explain_change(@ToolParam(description = "Plain-language summary") String summary) {
      events.accept(new AssistantEvent.Explanation(summary));
      return "Shown.";
    }

    private String store(ArtifactType type, String yaml) {
      draft.artifacts.put(type, yaml);
      events.accept(new AssistantEvent.DraftUpdate(type, yaml));
      List<ValidationIssue> issues = validator.validate(draft.artifacts);
      events.accept(new AssistantEvent.Issues(issues));
      return issues.isEmpty() ? "Stored. No issues." : "Stored. Issues: " + issues.size() + ". Call validate_draft.";
    }
  }
}
