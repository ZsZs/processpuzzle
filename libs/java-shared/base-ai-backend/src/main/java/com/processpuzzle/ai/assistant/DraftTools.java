package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools the model calls, for one conversation turn. Each call checks the caller's role and counts against the
 * turn's cap, and every result is text the model reads: a validation problem comes back as the issues to repair, so
 * the model can fix the draft and try again.
 *
 * <p>Instances are per turn, so the cap starts again with each turn. Tool results and the customer's text are
 * untrusted input; nothing here does more than write a draft.
 */
public class DraftTools {

    private final DefinitionDrafts drafts;
    private final AssistantAccess access;
    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();

    public DraftTools(DefinitionDrafts drafts, AssistantAccess access, int maxCalls) {
        this.drafts = drafts;
        this.access = access;
        this.maxCalls = maxCalls;
    }

    @Tool(name = "list_definitions", description = "List the keys of the drafts of one definition kind.")
    public String listDefinitions(@ToolParam(description = "The definition kind, e.g. entity or state") String kind) {
        return guarded(() -> drafts.list(access.currentOrganization(), kind).toString());
    }

    @Tool(name = "get_definition", description = "Read one draft as JSON in the shape get_schema describes.")
    public String getDefinition(@ToolParam(description = "The definition kind") String kind,
                                @ToolParam(description = "The key of the draft") String key) {
        return guarded(() -> drafts.get(access.currentOrganization(), kind, key).orElse("No draft '" + key + "' of kind " + kind));
    }

    @Tool(name = "get_schema", description = "The JSON Schema a draft of one definition kind must follow.")
    public String getSchema(@ToolParam(description = "The definition kind") String kind) {
        return guarded(() -> drafts.schema(kind));
    }

    @Tool(name = "save_draft", description = "Create or replace a DRAFT. It is never published; a person reviews it. "
            + "Returns the issues that stopped it, if any; repair them and save again.")
    public String saveDraft(@ToolParam(description = "The definition kind") String kind,
                            @ToolParam(description = "The key of the draft; the JSON's own key must match") String key,
                            @ToolParam(description = "The draft as JSON in the shape get_schema describes") String json) {
        return guarded(() -> describe(drafts.save(access.currentOrganization(), kind, key, json)));
    }

    @Tool(name = "validate_draft", description = "Check a stored draft against its rules and return the issues.")
    public String validateDraft(@ToolParam(description = "The definition kind") String kind,
                                @ToolParam(description = "The key of the draft") String key) {
        return guarded(() -> describe(drafts.validate(access.currentOrganization(), kind, key)));
    }

    /** Runs one tool call if the caller may design and the turn has calls left; otherwise says why not. */
    private String guarded(ToolCall call) {
        if (!access.mayDesign()) {
            return "Refused: the caller does not hold the definition-designer role.";
        }
        if (calls.incrementAndGet() > maxCalls) {
            return "Stop: this turn reached its limit of %d tool calls. Report what was done and what is left.".formatted(maxCalls);
        }
        try {
            return call.run();
        } catch (IllegalArgumentException e) {
            return "Refused: " + e.getMessage();
        }
    }

    private static String describe(List<AuthoringIssue> issues) {
        if (issues.isEmpty()) {
            return "Saved. No issues.";
        }
        return issues.stream()
            .map(issue -> issue.severity() + (issue.path().isEmpty() ? "" : " at " + issue.path()) + ": " + issue.text())
            .collect(Collectors.joining("\n"));
    }

    @FunctionalInterface
    private interface ToolCall {
        String run();
    }
}
