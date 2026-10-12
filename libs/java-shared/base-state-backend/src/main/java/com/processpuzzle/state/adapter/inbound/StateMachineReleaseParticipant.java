package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.StateMachineReleaser;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases state machines, each together with the layout drawn for it, keyed by the entity type it governs.
 *
 * <p><strong>What it refuses.</strong> The objects an entity type holds carry their current state in the machine's
 * state attribute, and are not migrated from one release to the next. So, while objects of the type exist:
 * <ul>
 *   <li>a state removed while objects are in it is an error — they would sit in a state the machine no longer has;</li>
 *   <li>a changed state attribute is an error — every object's state is stored under the old one;</li>
 *   <li>a machine removed is a warning — its objects keep their state value, but nothing governs it any longer.</li>
 * </ul>
 * A renamed state is a removal and an addition, and is judged as such. A changed layout alone is never a problem.
 */
@Component
public class StateMachineReleaseParticipant implements ReleaseParticipant {

    private final StateMachineDefinitionRepository machines;
    private final DiagramDefinitionRepository diagrams;
    private final StateMachineReleaser releaser;
    private final EntityObjectGateway objects;

    public StateMachineReleaseParticipant(StateMachineDefinitionRepository machines, DiagramDefinitionRepository diagrams,
                                          StateMachineReleaser releaser, EntityObjectGateway objects) {
        this.machines = machines;
        this.diagrams = diagrams;
        this.releaser = releaser;
        this.objects = objects;
    }

    @Override
    public String kind() {
        return DefinitionKinds.STATE;
    }

    @Override
    public int order() {
        return DefinitionKinds.STATE_ORDER;
    }

    @Override
    public Set<String> drafts(String orgKey) {
        return machinesOf(orgKey, Channel.DRAFT).keySet();
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, StateMachineDefinition> draftMachines = machinesOf(orgKey, Channel.DRAFT);
        Map<String, StateMachineDefinition> releasedMachines = machinesOf(orgKey, Channel.RELEASED);
        Map<String, DiagramDefinition> draftDiagrams = diagramsOf(orgKey, Channel.DRAFT);
        Map<String, DiagramDefinition> releasedDiagrams = diagramsOf(orgKey, Channel.RELEASED);
        Set<String> entityNames = new TreeSet<>(draftMachines.keySet());
        entityNames.addAll(releasedMachines.keySet());

        List<PendingChange> pending = new ArrayList<>();
        for (String entityName : entityNames) {
            PendingChange change = PendingChange.between(kind(), entityName,
                    fingerprint(draftMachines.get(entityName), draftDiagrams.get(entityName)),
                    fingerprint(releasedMachines.get(entityName), releasedDiagrams.get(entityName)));
            if (change != null) {
                pending.add(change);
            }
        }
        return pending;
    }

    @Override
    public List<DefinitionReference> references(String orgKey) {
        return machinesOf(orgKey, Channel.DRAFT).keySet().stream()
                .map(entityName -> DefinitionReference.required(kind(), entityName, DefinitionKinds.ENTITY, entityName))
                .toList();
    }

    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        Map<String, StateMachineDefinition> drafts = machinesOf(orgKey, Channel.DRAFT);
        List<ReleaseProblem> problems = new ArrayList<>();
        for (StateMachineDefinition current : machinesOf(orgKey, Channel.RELEASED).values()) {
            StateMachineDefinition draft = drafts.get(current.getEntityName());
            if (Objects.equals(machineContent(draft), machineContent(current))) {
                continue;
            }
            List<EntityObjectSnapshot> governed = objects.findObjects(orgKey, current.getEntityName());
            if (!governed.isEmpty()) {
                problems.addAll(problemsFor(current, draft, governed));
            }
        }
        return problems;
    }

    private List<ReleaseProblem> problemsFor(StateMachineDefinition current, StateMachineDefinition draft,
                                             List<EntityObjectSnapshot> governed) {
        String entityName = current.getEntityName();
        if (draft == null) {
            return List.of(ReleaseProblem.warning(kind(), entityName, "state.release.machineRemoved",
                    "'%s' loses its state machine; its %d object(s) keep their state, but nothing governs it any longer."
                            .formatted(entityName, governed.size())));
        }
        if (!Objects.equals(current.getStateAttributeKey(), draft.getStateAttributeKey())) {
            return List.of(ReleaseProblem.error(kind(), entityName, "state.release.stateAttributeChanged",
                    "'%s' keeps its state in '%s'; its %d object(s) would be read from '%s' instead."
                            .formatted(entityName, current.getStateAttributeKey(), governed.size(),
                                    draft.getStateAttributeKey())));
        }
        Set<String> kept = draft.getStates().stream().map(State::key).collect(Collectors.toSet());
        Map<String, Long> inState = governed.stream()
                .map(object -> object.attribute(current.getStateAttributeKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Object::toString, Collectors.counting()));
        List<ReleaseProblem> problems = new ArrayList<>();
        current.getStates().stream()
                .map(State::key)
                .filter(stateKey -> !kept.contains(stateKey) && inState.getOrDefault(stateKey, 0L) > 0)
                .forEach(stateKey -> problems.add(ReleaseProblem.error(kind(), entityName, "state.release.stateRemovedWithObjects",
                        "'%s' removes state '%s', which %d object(s) are in; move them out of it first."
                                .formatted(entityName, stateKey, inState.get(stateKey)))));
        return problems;
    }

    @Override
    public void release(String orgKey) {
        releaser.releaseAll(orgKey);
    }

    private Map<String, StateMachineDefinition> machinesOf(String orgKey, Channel channel) {
        return machines.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(StateMachineDefinition::getEntityName, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    private Map<String, DiagramDefinition> diagramsOf(String orgKey, Channel channel) {
        return diagrams.findByOrgKeyAndChannel(orgKey, channel).stream()
                .collect(Collectors.toMap(DiagramDefinition::getEntityName, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    /** The machine and its layout as one definition; {@code null} when there is no machine. */
    static String fingerprint(StateMachineDefinition machine, DiagramDefinition diagram) {
        if (machine == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("machine", machineContent(machine));
        if (diagram != null) {
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("nodes", diagram.getNodes());
            layout.put("edges", diagram.getEdges());
            layout.put("viewport", diagram.getViewport());
            content.put("diagram", layout);
        }
        return CanonicalJson.of(content, Set.of());
    }

    /** The authored content only: neither the channel, nor the version and timestamps every save moves. */
    private static Map<String, Object> machineContent(StateMachineDefinition machine) {
        if (machine == null) {
            return null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", machine.getName());
        content.put("description", machine.getDescription());
        content.put("stateAttributeKey", machine.getStateAttributeKey());
        content.put("initialStateKey", machine.getInitialStateKey());
        content.put("states", machine.getStates());
        content.put("transitions", machine.getTransitions());
        return content;
    }
}
