package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Keeps drafts in memory, keyed by the definition key; refuses any draft whose JSON is {@code refuse}. */
final class InMemoryAuthoringParticipant implements DefinitionAuthoringParticipant {

    private final String kind;
    private final Map<String, String> drafts = new TreeMap<>();

    InMemoryAuthoringParticipant(String kind) {
        this.kind = kind;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String schema() {
        return "schema:" + kind;
    }

    @Override
    public List<String> list(String orgKey) {
        return new ArrayList<>(drafts.keySet());
    }

    @Override
    public Optional<String> get(String orgKey, String key) {
        return Optional.ofNullable(drafts.get(key));
    }

    @Override
    public List<AuthoringIssue> saveDraft(String orgKey, String key, String json) {
        if ("refuse".equals(json)) {
            return List.of(AuthoringIssue.error("", "refused"));
        }
        drafts.put(key, json);
        return List.of();
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, String key) {
        return List.of();
    }
}
