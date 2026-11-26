package com.processpuzzle.ai.assist;

import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The authoring assist over HTTP: which tasks the caller can use, and one task run on a description. The organization
 * in the path must be the caller's own; whether they may edit the task's target is the task's own check. Refusals other
 * than the request's shape are mapped by the module's exception handler. Only exists when the assistant is enabled.
 */
@RestController
@ConditionalOnProperty(prefix = "processpuzzle.ai.assistant", name = "enabled", havingValue = "true")
public class AuthoringAssistEndpoint {

    static final int MAX_TEXT_LENGTH = 4000;
    static final int MAX_CURRENT_LENGTH = 20_000;
    static final int MAX_PARAMETERS = 20;
    static final int MAX_PARAMETER_LENGTH = 500;

    private final AuthoringAssist assist;
    private final AssistantAccess access;

    public AuthoringAssistEndpoint(AuthoringAssist assist, AssistantAccess access) {
        this.assist = assist;
        this.access = access;
    }

    @GetMapping(value = "/organizations/{orgKey}/ai/assist", produces = MediaType.APPLICATION_JSON_VALUE)
    public Tasks tasks(@PathVariable String orgKey) {
        requireOwnOrganization(orgKey);
        return new Tasks(assist.availableTasks(orgKey));
    }

    @PostMapping(value = "/organizations/{orgKey}/ai/assist/{task}",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Response run(@PathVariable String orgKey, @PathVariable String task, @RequestBody Request request) {
        requireOwnOrganization(orgKey);
        AssistRequest checked = check(request);
        AssistResult result = assist.assist(orgKey, task, checked);
        return new Response(result.candidate(), result.explanation(), result.assumptions(), result.issues(),
            result.rounds(), result.model());
    }

    private void requireOwnOrganization(String orgKey) {
        if (!orgKey.equals(access.currentOrganization())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The caller does not act for this organization");
        }
    }

    private static AssistRequest check(Request request) {
        if (request == null || request.text() == null || request.text().isBlank()
            || request.text().length() > MAX_TEXT_LENGTH) {
            throw bad("The text must be between 1 and %d characters".formatted(MAX_TEXT_LENGTH));
        }
        if (request.current() != null && request.current().length() > MAX_CURRENT_LENGTH) {
            throw bad("The current value may have at most %d characters".formatted(MAX_CURRENT_LENGTH));
        }
        Map<String, String> parameters = request.parameters() == null ? Map.of() : request.parameters();
        boolean tooMany = parameters.size() > MAX_PARAMETERS;
        boolean invalidValue = parameters.values().stream().anyMatch(v -> v == null || v.length() > MAX_PARAMETER_LENGTH);
        if (tooMany || invalidValue) {
            throw bad("At most %d parameters of at most %d characters each".formatted(MAX_PARAMETERS, MAX_PARAMETER_LENGTH));
        }
        return new AssistRequest(request.text(), parameters, request.current());
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** The user's description, the other form values the task asks for, and the field's present value. */
    public record Request(String text, Map<String, String> parameters, String current) {
    }

    /** The best candidate with what the model says about it and what is still wrong with it. */
    public record Response(String candidate, String explanation, List<String> assumptions, List<AuthoringIssue> issues,
                           int rounds, String model) {
    }

    /** The tasks available to the caller; empty when the organization has no AI subscription. */
    public record Tasks(List<String> tasks) {
    }
}
