package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.shared.event.OccurredParameter;
import com.processpuzzle.workflow.definition.usecases.outbound.CatalogParameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Whether a workflow can fill the required parameters of the events it throws (docs/event-parameters.md, phase 2).
 *
 * <p>A running throw fills a parameter as {@code ThrownParameters} does: from its own payload mapping, otherwise from
 * the plain context variable of that name, otherwise from the one earlier event that carried a parameter of that name.
 * Statically, a name is <em>produced upstream</em> of the throw when
 * <ul>
 *   <li>the throw's payload mapping names it; or</li>
 *   <li>it is a plain variable something before the throw writes — a start event's or an upstream catch's payload
 *   mapping target, an upstream task's step {@code outputMapping} target, or an artifact's object name; or</li>
 *   <li>exactly one of the start events, taken together, and the upstream catches carries a parameter of that name —
 *   a SYSTEM event its {@code subject} too. Start events are alternatives, a run begins on one of them, so they count
 *   once.</li>
 * </ul>
 *
 * <p>Upstream is what the throw depends on, transitively; a boundary event depends on the task it is attached to. A
 * join of ANY is read as if every branch ran — the check refuses what no path could fill, not what one path might
 * miss. A name only a task's completion could supply — its free key/value context — cannot be known statically: that
 * is a {@link Reason#TASK_ONLY} warning. An event the catalog cannot tell parameters of is skipped.
 */
public final class ThrowParameterCoverage {

    public enum Reason {
        /** Nothing upstream produces it. An error. */
        UNFILLED,
        /** Several upstream events carry it and no plain variable settles which. An error. */
        AMBIGUOUS,
        /** Only an upstream task's completion context could supply it. A warning. */
        TASK_ONLY
    }

    /** One required parameter a throw may send without. */
    public record Shortfall(String eventUseId, String eventDefinitionId, String parameter, Reason reason,
                            List<String> carriers) {}

    private ThrowParameterCoverage() {}

    /**
     * The required parameters {@code workflow}'s throws may not fill, in event order.
     *
     * @param parameters the parameters a catalog event declares, by event definition id
     * @param kinds      a catalog event's kind, by event definition id
     * @param tasks      the task definition a workflow names, by id
     */
    public static List<Shortfall> shortfallsOf(Workflow workflow, Function<String, List<CatalogParameter>> parameters,
                                               Function<String, Optional<CatalogEventKind>> kinds,
                                               Function<String, Optional<TaskDefinition>> tasks) {
        List<Shortfall> shortfalls = new ArrayList<>();
        Map<String, List<String>> dependencies = dependenciesOf(workflow);
        for (EventUse use : nonNull(workflow.getEvents())) {
            if (use.isThrow() && !isBlank(use.getEventDefinitionId())) {
                shortfalls.addAll(shortfallsOf(workflow, use, dependencies, parameters, kinds, tasks));
            }
        }
        return shortfalls;
    }

    /** The required parameters of one throw's event that it may not fill. */
    private static List<Shortfall> shortfallsOf(Workflow workflow, EventUse use, Map<String, List<String>> dependencies,
                                                Function<String, List<CatalogParameter>> parameters,
                                                Function<String, Optional<CatalogEventKind>> kinds,
                                                Function<String, Optional<TaskDefinition>> tasks) {
        List<CatalogParameter> required = nonNull(parameters.apply(use.getEventDefinitionId())).stream()
                .filter(CatalogParameter::required)
                .toList();
        if (required.isEmpty()) {
            return List.of();
        }
        Upstream upstream = upstreamOf(workflow, use, dependencies, parameters, kinds, tasks);
        return required.stream()
                .flatMap(parameter -> shortfallOf(use, parameter.name(), upstream).stream())
                .toList();
    }

    private static Optional<Shortfall> shortfallOf(EventUse use, String name, Upstream upstream) {
        if (nonNull(use.getPayloadMapping()).containsKey(name) || upstream.plain().contains(name)) {
            return Optional.empty();
        }
        List<String> carriers = upstream.carriers().getOrDefault(name, List.of());
        if (carriers.size() == 1) {
            return Optional.empty();
        }
        return Optional.of(new Shortfall(use.getId(), use.getEventDefinitionId(), name, reasonOf(carriers, upstream), carriers));
    }

    private static Reason reasonOf(List<String> carriers, Upstream upstream) {
        if (carriers.size() > 1) {
            return Reason.AMBIGUOUS;
        }
        return upstream.hasTask() ? Reason.TASK_ONLY : Reason.UNFILLED;
    }

    /** What the throw can read: the plain variables written before it, and by name the events that carried a value. */
    private record Upstream(Set<String> plain, Map<String, List<String>> carriers, boolean hasTask) {}

    private static Upstream upstreamOf(Workflow workflow, EventUse use, Map<String, List<String>> dependencies,
                                       Function<String, List<CatalogParameter>> parameters,
                                       Function<String, Optional<CatalogEventKind>> kinds,
                                       Function<String, Optional<TaskDefinition>> tasks) {
        Set<String> before = reachableFrom(use.getDependsOn(), dependencies);
        Set<String> plain = new HashSet<>();
        Map<String, List<String>> carriers = new HashMap<>();

        nonNull(workflow.getArtifacts()).stream()
                .map(ArtifactUse::getObjectName)
                .filter(name -> !isBlank(name))
                .forEach(plain::add);
        Map<String, String> carriedByStart = new LinkedHashMap<>();
        for (StartEvent start : nonNull(workflow.getStartEvents())) {
            plain.addAll(nonNull(start.getPayloadMapping()).keySet());
            if (start.getStartType() == WorkflowStartConditionType.TRIGGERING_EVENT && !isBlank(start.getEventType())) {
                namesCarriedBy(start.getEventType(), parameters, kinds).forEach(name -> carriedByStart.putIfAbsent(name, start.getId()));
            }
        }
        carriedByStart.forEach((name, startId) -> carriers.computeIfAbsent(name, key -> new ArrayList<>()).add(startId));

        boolean hasTask = false;
        for (EventUse event : nonNull(workflow.getEvents())) {
            if (event.isCatch() && before.contains(event.getId())) {
                plain.addAll(nonNull(event.getPayloadMapping()).keySet());
                if (!isBlank(event.getEventDefinitionId())) {
                    namesCarriedBy(event.getEventDefinitionId(), parameters, kinds)
                            .forEach(name -> carriers.computeIfAbsent(name, key -> new ArrayList<>()).add(event.getId()));
                }
            }
        }
        for (TaskUse task : nonNull(workflow.getTasks())) {
            if (before.contains(task.getTaskDefinitionId())) {
                hasTask = true;
                tasks.apply(task.getTaskDefinitionId()).ifPresent(definition -> nonNull(definition.getSteps()).stream()
                        .map(StepDefinition::getOutputMapping)
                        .forEach(mapping -> plain.addAll(nonNull(mapping).keySet())));
            }
        }
        return new Upstream(plain, carriers, hasTask);
    }

    private static Set<String> namesCarriedBy(String eventDefinitionId, Function<String, List<CatalogParameter>> parameters,
                                              Function<String, Optional<CatalogEventKind>> kinds) {
        Set<String> names = new LinkedHashSet<>();
        if (kinds.apply(eventDefinitionId).filter(kind -> kind == CatalogEventKind.SYSTEM).isPresent()) {
            names.add(OccurredParameter.SUBJECT);
        }
        nonNull(parameters.apply(eventDefinitionId)).forEach(parameter -> names.add(parameter.name()));
        return names;
    }

    /** Every task and event by id, with what it waits on: its {@code dependsOn}, and a boundary its task. */
    private static Map<String, List<String>> dependenciesOf(Workflow workflow) {
        Map<String, List<String>> dependencies = new HashMap<>();
        nonNull(workflow.getTasks()).forEach(task -> dependencies.put(task.getTaskDefinitionId(), nonNull(task.getDependsOn())));
        for (EventUse event : nonNull(workflow.getEvents())) {
            List<String> waitsOn = new ArrayList<>(nonNull(event.getDependsOn()));
            if (!isBlank(event.getAttachedTo())) {
                waitsOn.add(event.getAttachedTo());
            }
            dependencies.put(event.getId(), waitsOn);
        }
        return dependencies;
    }

    private static Set<String> reachableFrom(List<String> roots, Map<String, List<String>> dependencies) {
        Set<String> reached = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(nonNull(roots));
        while (!pending.isEmpty()) {
            String id = pending.pop();
            if (reached.add(id)) {
                pending.addAll(dependencies.getOrDefault(id, List.of()));
            }
        }
        return reached;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }
}
