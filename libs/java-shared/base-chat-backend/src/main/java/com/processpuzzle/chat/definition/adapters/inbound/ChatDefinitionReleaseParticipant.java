package com.processpuzzle.chat.definition.adapters.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.usecases.outbound.PermitAllSubjectStatesPort;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStates;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStatesPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases chat definitions: copies each draft onto its released row, and deletes the released rows whose draft was
 * deleted. The running application reads the released rows only.
 *
 * <p>A chat definition names an entity type — the kind of record that gets the chat — so it refers to that entity
 * definition, and a release that would leave it dangling is refused. A STATE_BOUND one also names states of that
 * entity: the check asks, through {@link SubjectStatesPort}, whether the entity has a state machine and whether each
 * writable state is one of its states, because a chat bound to a state nobody can reach would be read-only for ever and
 * nobody would notice until it was used.
 *
 * <p>Conversations are runtime data, not definitions: they are not released and a release does not touch them.
 */
@Component
public class ChatDefinitionReleaseParticipant implements ReleaseParticipant {

    private final ChatDefinitionRepository repository;
    private final SubjectStatesPort states;

    public ChatDefinitionReleaseParticipant(ChatDefinitionRepository repository, ObjectProvider<SubjectStatesPort> states) {
        this.repository = repository;
        this.states = states.getIfUnique(PermitAllSubjectStatesPort::new);
    }

    @Override
    public String kind() {
        return DefinitionKinds.CHAT;
    }

    @Override
    public int order() {
        return DefinitionKinds.CHAT_ORDER;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> drafts(String orgKey) {
        return byKey(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingChange> pending(String orgKey) {
        Map<String, ChatDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ChatDefinition> released = byKey(orgKey, Channel.RELEASED);
        Set<String> keys = new TreeSet<>(drafts.keySet());
        keys.addAll(released.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String key : keys) {
            PendingChange change = PendingChange.between(kind(), key, fingerprint(drafts.get(key)), fingerprint(released.get(key)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DefinitionReference> references(String orgKey) {
        return byKey(orgKey, Channel.DRAFT).values().stream()
                .map(definition -> DefinitionReference.required(kind(), definition.getDefinitionKey(), DefinitionKinds.ENTITY, definition.getSubjectDefinition()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        List<ReleaseProblem> problems = new ArrayList<>();
        for (ChatDefinition definition : byKey(orgKey, Channel.DRAFT).values()) {
            // A missing entity is the reference's error already; asking about its states as well would only repeat it.
            boolean entityMissing = candidate.manages(DefinitionKinds.ENTITY) && !candidate.exists(DefinitionKinds.ENTITY, definition.getSubjectDefinition());
            if (definition.getLifecycle() == ChatLifecycle.STATE_BOUND && !entityMissing) {
                problems.addAll(checkWritableStates(orgKey, definition));
            }
        }
        return problems;
    }

    private List<ReleaseProblem> checkWritableStates(String orgKey, ChatDefinition definition) {
        Optional<SubjectStates> answer = states.statesOf(orgKey, definition.getSubjectType(), definition.getSubjectDefinition(), Channel.DRAFT);
        if (answer.isEmpty()) {
            return List.of();
        }
        String key = definition.getDefinitionKey();
        if (!answer.get().governed()) {
            return List.of(ReleaseProblem.error(kind(), key, "chat.release.no-state-machine",
                    "Chat '%s' is bound to the state of '%s', which no state machine governs.".formatted(key, definition.getSubjectDefinition())));
        }
        return definition.getWritableStates().stream()
                .filter(state -> !answer.get().keys().contains(state))
                .map(state -> ReleaseProblem.error(kind(), key, "chat.release.unknown-state",
                        "Chat '%s' is writable in state '%s', which '%s' does not have.".formatted(key, state, definition.getSubjectDefinition())))
                .toList();
    }

    @Override
    @Transactional
    public void release(String orgKey) {
        Map<String, ChatDefinition> drafts = byKey(orgKey, Channel.DRAFT);
        Map<String, ChatDefinition> released = byKey(orgKey, Channel.RELEASED);
        for (ChatDefinition draft : drafts.values()) {
            ChatDefinition current = released.get(draft.getDefinitionKey());
            if (current == null) {
                repository.save(ChatDefinition.releasedCopyOf(draft));
            } else {
                current.replaceWith(draft);
                repository.save(current);
            }
        }
        repository.deleteAll(released.values().stream().filter(row -> !drafts.containsKey(row.getDefinitionKey())).toList());
    }

    private Map<String, ChatDefinition> byKey(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, channel).stream()
                .collect(Collectors.toMap(ChatDefinition::getDefinitionKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static String fingerprint(ChatDefinition definition) {
        return definition == null ? null : definition.fingerprint();
    }
}
