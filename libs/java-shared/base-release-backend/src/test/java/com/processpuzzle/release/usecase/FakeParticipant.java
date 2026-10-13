package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A participant whose drafts, pending changes, references and problems a test sets, and which logs what it is asked to do. */
class FakeParticipant implements ReleaseParticipant {

    final Set<String> drafts = new LinkedHashSet<>();
    /** Further keys the drafts may be referred to by, beside their own. */
    final Set<String> aliases = new LinkedHashSet<>();
    final List<PendingChange> pending = new ArrayList<>();
    final List<DefinitionReference> references = new ArrayList<>();
    final List<ReleaseProblem> problems = new ArrayList<>();
    Runnable onRelease = () -> { };

    private final String kind;
    private final int order;
    private final List<String> calls;

    FakeParticipant(String kind, int order, List<String> calls) {
        this.kind = kind;
        this.order = order;
        this.calls = calls;
    }

    FakeParticipant withDraft(String key, PendingChange.Change change) {
        drafts.add(key);
        pending.add(new PendingChange(kind, key, change, "{\"key\":\"" + key + "\"}"));
        return this;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return drafts;
    }

    @Override
    public Set<String> referableKeys(String orgKey) {
        Set<String> keys = new LinkedHashSet<>(drafts);
        keys.addAll(aliases);
        return keys;
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return references;
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return problems;
    }

    @Override
    public void release(String orgKey) {
        calls.add("release " + kind);
        onRelease.run();
    }
}
