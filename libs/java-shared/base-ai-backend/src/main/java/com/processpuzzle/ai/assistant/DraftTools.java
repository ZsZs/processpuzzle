package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.definition.AuthoringIssue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The tools the model calls, for one conversation turn. Each call checks the caller's role and counts against the
 * turn's cap, and every result is text the model reads: a validation problem comes back as the issues to repair, so
 * the model can fix the draft and try again. Each call is written to the audit trail with its outcome.
 *
 * <p>A draft the validators turn back may be repaired and saved again, up to the turn's repair limit; after that the
 * draft is left for a person, and the assistant must say so.
 *
 * <p>Instances are per turn, so the caps start again with each turn, and the {@link Caller} is the one the turn
 * started with: nothing here reads the security context. Tool results and the customer's text are untrusted input;
 * nothing here does more than write a draft.
 */
public class DraftTools {

    private final DefinitionDrafts drafts;
    private final Caller caller;
    private final int maxCalls;
    private final int maxRepairRounds;
    private final AssistantAuditLog audit;
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, Integer> returnedWithIssues = new ConcurrentHashMap<>();

    public DraftTools(DefinitionDrafts drafts, Caller caller, int maxCalls, int maxRepairRounds, AssistantAuditLog audit) {
        this.drafts = drafts;
        this.caller = caller;
        this.maxCalls = maxCalls;
        this.maxRepairRounds = maxRepairRounds;
        this.audit = audit;
    }

    /** The tool calls the turn has made so far, refused and stopped ones included. */
    public int toolCalls() {
        return calls.get();
    }

    @Tool(name = "list_definitions", description = "List the keys of the drafts of one definition kind.")
    public String listDefinitions(@ToolParam(description = "The definition kind, e.g. entity or state") String kind) {
        return guarded("list_definitions", () -> Result.ok(drafts.list(caller.organization(), kind).toString()), kind);
    }

    @Tool(name = "get_definition", description = "Read one draft as JSON in the shape get_schema describes.")
    public String getDefinition(@ToolParam(description = "The definition kind") String kind,
                                @ToolParam(description = "The key of the draft") String key) {
        return guarded("get_definition", () -> Result.ok(
            drafts.get(caller.organization(), kind, key).orElse("No draft '" + key + "' of kind " + kind)),
            kind, key);
    }

    @Tool(name = "get_schema", description = "The JSON Schema a draft of one definition kind must follow.")
    public String getSchema(@ToolParam(description = "The definition kind") String kind) {
        return guarded("get_schema", () -> Result.ok(drafts.schema(kind)), kind);
    }

    @Tool(name = "save_draft", description = "Create or replace a DRAFT. It is never published; a person reviews it. "
            + "Returns the issues that stopped it, if any; repair them and save again.")
    public String saveDraft(@ToolParam(description = "The definition kind") String kind,
                            @ToolParam(description = "The key of the draft; the JSON's own key must match") String key,
                            @ToolParam(description = "The draft as JSON in the shape get_schema describes") String json) {
        return guarded("save_draft", () -> {
            String draft = kind + "/" + key;
            if (returnedWithIssues.getOrDefault(draft, 0) >= maxRepairRounds) {
                return new Result("Stop: this draft was returned with issues %d times in this turn. Report what is wrong instead of saving it again."
                    .formatted(maxRepairRounds), "stopped-repairs");
            }
            List<AuthoringIssue> issues = drafts.save(caller.organization(), kind, key, json);
            if (issues.isEmpty()) {
                return Result.ok("Saved. No issues.");
            }
            returnedWithIssues.merge(draft, 1, Integer::sum);
            return Result.issues(describe(issues));
        }, kind, key, json);
    }

    @Tool(name = "validate_draft", description = "Check a stored draft against its rules and return the issues.")
    public String validateDraft(@ToolParam(description = "The definition kind") String kind,
                                @ToolParam(description = "The key of the draft") String key) {
        return guarded("validate_draft", () -> {
            List<AuthoringIssue> issues = drafts.validate(caller.organization(), kind, key);
            return issues.isEmpty() ? Result.ok("No issues.") : Result.issues(describe(issues));
        }, kind, key);
    }

    /**
     * Runs one tool call if the caller may design and the turn has calls left; otherwise says why not. Every outcome
     * is audited, with a hash of the arguments rather than the arguments themselves.
     */
    private String guarded(String tool, Action action, String... arguments) {
        String hash = ArgumentHash.of(arguments);
        if (!caller.mayDesign()) {
            audit(tool, hash, "refused-role");
            return "Refused: the caller does not hold the definition-designer role.";
        }
        if (calls.incrementAndGet() > maxCalls) {
            audit(tool, hash, "stopped-limit");
            return "Stop: this turn reached its limit of %d tool calls. Report what was done and what is left.".formatted(maxCalls);
        }
        try {
            Result result = action.run();
            audit(tool, hash, result.outcome());
            return result.text();
        } catch (IllegalArgumentException e) {
            audit(tool, hash, "refused");
            return "Refused: " + e.getMessage();
        }
    }

    private void audit(String tool, String hash, String outcome) {
        audit.toolCall(new AssistantAuditLog.ToolCall(Instant.now(), caller.principal(), caller.organization(), tool, hash, outcome));
    }

    private static String describe(List<AuthoringIssue> issues) {
        return issues.stream()
            .map(issue -> issue.severity() + (issue.path().isEmpty() ? "" : " at " + issue.path()) + ": " + issue.text())
            .collect(Collectors.joining("\n"));
    }

    /** What a tool call said, and how it went: a clean result, or a draft the validators turned back. */
    private record Result(String text, String outcome) {

        static Result ok(String text) {
            return new Result(text, "ok");
        }

        static Result issues(String text) {
            return new Result(text, "issues");
        }
    }

    @FunctionalInterface
    private interface Action {
        Result run();
    }
}
