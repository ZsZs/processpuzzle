package com.processpuzzle.ai.assistant;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The draft operations the design assistant may perform, over every definition kind the platform offers. It is a
 * thin dispatcher: each kind's {@link DefinitionAuthoringParticipant} does the work, with the same validators as the
 * REST endpoints. A kind is addressed by name, so the assistant's tool surface stays the same as kinds are added.
 *
 * <p>It only ever writes drafts. Publishing is not reachable from here, by design: a release stays with a person.
 */
public class DefinitionDrafts {

    private final Map<String, DefinitionAuthoringParticipant> participants;

    public DefinitionDrafts(List<DefinitionAuthoringParticipant> participants) {
        this.participants = participants.stream().collect(Collectors.toMap(
            DefinitionAuthoringParticipant::kind,
            Function.identity(),
            (first, second) -> {
                throw new IllegalStateException("Two authoring participants claim the kind '" + first.kind() + "'");
            },
            () -> new TreeMap<>(Comparator.naturalOrder())));
    }

    /** The kinds that can be authored, in name order. */
    public Set<String> kinds() {
        return participants.keySet();
    }

    /**
     * @throws UnknownDefinitionKindException when no participant authors the kind
     */
    public String schema(String kind) {
        return participant(kind).schema();
    }

    public List<String> list(String orgKey, String kind) {
        return participant(kind).list(orgKey);
    }

    public Optional<String> get(String orgKey, String kind, String key) {
        return participant(kind).get(orgKey, key);
    }

    /** Writes the draft; returns no issues when it was saved, otherwise the issues that stopped it. */
    public List<AuthoringIssue> save(String orgKey, String kind, String key, String json) {
        return participant(kind).saveDraft(orgKey, key, json);
    }

    public List<AuthoringIssue> validate(String orgKey, String kind, String key) {
        return participant(kind).validate(orgKey, key);
    }

    private DefinitionAuthoringParticipant participant(String kind) {
        DefinitionAuthoringParticipant participant = participants.get(kind);
        if (participant == null) {
            throw new UnknownDefinitionKindException(kind, participants.keySet());
        }
        return participant;
    }
}
