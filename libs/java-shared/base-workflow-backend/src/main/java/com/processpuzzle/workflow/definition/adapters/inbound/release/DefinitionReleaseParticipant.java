package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.workflow.definition.domain.ReleasableDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases one kind of workflow definition: copies each draft onto its released row, updating that row in place, and
 * deletes the released rows whose draft was deleted. The running application executes the released rows only.
 *
 * <p>The kinds of base-workflow differ only in their fields and in what they refer to, so a subclass names those and
 * nothing else: which rows a channel holds, a row's key, its authored content (what a release compares), how a draft
 * is copied onto a released row, and its references. What a release does besides, the subclass hears of in
 * {@link #released}, inside the release's transaction.
 *
 * @param <D> the definition
 */
public abstract class DefinitionReleaseParticipant<D extends ReleasableDefinition> implements ReleaseParticipant {

    private final String kind;
    private final int order;
    private final JpaRepository<D, ?> repository;

    protected DefinitionReleaseParticipant(String kind, int order, JpaRepository<D, ?> repository) {
        this.kind = kind;
        this.order = order;
        this.repository = repository;
    }

    /** The organization's rows of this kind in the channel. */
    protected abstract List<D> rows(String orgKey, Channel channel);

    /** The row's key within its kind and organization. */
    protected abstract String keyOf(D row);

    /** The authored content only: neither the key nor the channel, version and audit fields every save moves. */
    protected abstract Map<String, Object> contentOf(D row);

    /** A new, empty row of the draft's key, which {@link #copy} fills: what a first release creates. */
    protected abstract D blankLike(D draft);

    /** Copies the authored content of {@code draft} onto {@code released}, leaving its key, version and audit fields. */
    protected abstract void copy(D draft, D released);

    /** What the draft refers to; none unless the kind says otherwise. */
    protected List<DefinitionReference> referencesOf(D draft) {
        return List.of();
    }

    /**
     * What the existing data objects to, in the draft and released rows by key; none unless the kind says otherwise.
     */
    protected List<ReleaseProblem> problemsOf(String orgKey, Map<String, D> drafts, Map<String, D> released) {
        return List.of();
    }

    /**
     * Told, inside the release's transaction, which rows it released (added or changed) and which it removed — for a
     * kind whose release has an effect outside the definition tables. Nothing by default.
     */
    protected void released(String orgKey, List<D> releasedRows, Set<String> removedKeys) {
        // nothing outside the definition tables
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
        return byKey(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, D> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, D> released = byKey(orgKey, Channel.RELEASED);
        Set<String> keys = new TreeSet<>(drafts.keySet());
        keys.addAll(released.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String key : keys) {
            PendingChange change = PendingChange.between(kind, key, fingerprint(drafts.get(key)), fingerprint(released.get(key)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return byKey(orgKey, Channel.DRAFT).values().stream().flatMap(draft -> referencesOf(draft).stream()).toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return problemsOf(orgKey, byKey(orgKey, Channel.DRAFT), byKey(orgKey, Channel.RELEASED));
    }

    @Override
    public void release(String orgKey) {
        release(orgKey, byKey(orgKey, Channel.DRAFT).keySet(), true);
    }

    /**
     * Releases only the drafts of these keys: what a loader seeding a vetted set does with what it wrote, without
     * releasing a designer's unrelated drafts.
     */
    @Transactional
    public void release(String orgKey, Collection<String> keys) {
        release(orgKey, keys, false);
    }

    private void release(String orgKey, Collection<String> keys, boolean removeTheRest) {
        Map<String, D> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, D> released = byKey(orgKey, Channel.RELEASED);
        List<D> releasedRows = new ArrayList<>();
        for (String key : keys) {
            D draft = drafts.get(key);
            if (draft == null) {
                continue;
            }
            D current = released.get(key);
            if (current != null && Objects.equals(fingerprint(draft), fingerprint(current))) {
                continue;
            }
            if (current == null) {
                current = blankLike(draft);
                current.setChannel(Channel.RELEASED);
            }
            copy(draft, current);
            releasedRows.add(repository.save(current));
        }
        Set<String> removed = new TreeSet<>();
        if (removeTheRest) {
            List<D> gone = released.values().stream().filter(row -> !drafts.containsKey(keyOf(row))).toList();
            repository.deleteAll(gone);
            gone.forEach(row -> removed.add(keyOf(row)));
        }
        released(orgKey, releasedRows, removed);
    }

    /** The organization's rows of this kind in the channel, by key. */
    protected Map<String, D> byKey(String orgKey, Channel channel) {
        return rows(orgKey, channel).stream()
                .collect(Collectors.toMap(this::keyOf, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private String fingerprint(D row) {
        return row == null ? null : CanonicalJson.of(contentOf(row), Set.of());
    }
}
