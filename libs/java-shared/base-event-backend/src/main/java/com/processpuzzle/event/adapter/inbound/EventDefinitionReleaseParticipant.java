package com.processpuzzle.event.adapter.inbound;

import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases event definitions: copies each draft onto its released row, and deletes the released rows whose draft was
 * deleted. The running application matches platform facts and thrown events against the released catalog only.
 *
 * <p>A SYSTEM event names the entity type its facts are about, so it refers to that entity definition. An event holds
 * no data of its own: whether a running workflow still waits for a removed one is a reference of the workflow, which
 * the release checks on the workflow's side.
 */
@Component
public class EventDefinitionReleaseParticipant implements ReleaseParticipant {

    private final EventDefinitionRepository repository;

    public EventDefinitionReleaseParticipant(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public String kind() {
        return DefinitionKinds.EVENT;
    }

    @Override
    public int order() {
        return DefinitionKinds.EVENT_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return byId(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, EventDefinition> drafts = byId(orgKey, Channel.DRAFT);
        Map<String, EventDefinition> released = byId(orgKey, Channel.RELEASED);
        Set<String> ids = new TreeSet<>(drafts.keySet());
        ids.addAll(released.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String id : ids) {
            PendingChange change = PendingChange.between(kind(), id, fingerprint(drafts.get(id)), fingerprint(released.get(id)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return byId(orgKey, Channel.DRAFT).values().stream()
                .filter(event -> event.getSubjectType() != null && !event.getSubjectType().isBlank())
                .map(event -> DefinitionReference.required(kind(), event.getId(), DefinitionKinds.ENTITY, event.getSubjectType()))
                .toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        return List.of();
    }

    @Override
    public void release(String orgKey) {
        release(orgKey, byId(orgKey, Channel.DRAFT).keySet(), true);
    }

    /**
     * Releases only the drafts of these ids: what a loader seeding a vetted set does with what it wrote, without
     * releasing a designer's unrelated drafts.
     */
    @Transactional
    public void release(String orgKey, Collection<String> ids) {
        release(orgKey, ids, false);
    }

    private void release(String orgKey, Collection<String> ids, boolean removeTheRest) {
        Map<String, EventDefinition> drafts = byId(orgKey, Channel.DRAFT);
        Map<String, EventDefinition> released = byId(orgKey, Channel.RELEASED);
        for (String id : ids) {
            EventDefinition draft = drafts.get(id);
            if (draft == null) {
                continue;
            }
            EventDefinition current = released.get(id);
            if (current == null) {
                repository.save(EventDefinition.releasedCopyOf(draft));
            } else {
                current.replaceWith(draft);
                repository.save(current);
            }
        }
        if (removeTheRest) {
            repository.deleteAll(released.values().stream().filter(row -> !drafts.containsKey(row.getId())).toList());
        }
    }

    private Map<String, EventDefinition> byId(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, channel).stream()
                .collect(Collectors.toMap(EventDefinition::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    static String fingerprint(EventDefinition event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", event.getName());
        content.put("description", event.getDescription());
        content.put("kind", event.getKind());
        content.put("subjectType", event.getSubjectType());
        content.put("action", event.getAction());
        content.put("state", event.getState());
        content.put("parameters", event.getParameters());
        return CanonicalJson.of(content, Set.of());
    }
}
