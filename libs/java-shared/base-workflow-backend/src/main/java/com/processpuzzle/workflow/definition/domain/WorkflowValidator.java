package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.shared.event.CatalogEventKind;
import com.processpuzzle.workflow.common.ValidationException;
import com.processpuzzle.workflow.definition.usecases.outbound.EventCatalogPort;
import com.processpuzzle.workflow.definition.usecases.outbound.PermitAllEventCatalogPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Referential integrity of a {@link Workflow} against the organization's catalog. A cross-aggregate
 * check rather than a within-document one: roles, artifacts, tools and tasks live in aggregates of
 * their own, so what would otherwise be "this task's performedBy names a role in the same document"
 * is a lookup.
 *
 * <p>It runs when a workflow is <em>saved</em>, not when an instance starts, which is what makes the
 * catalog delete guards ({@code DeleteRoleDefinitionUseCase} and its siblings) the other half of
 * the same invariant: one refuses a workflow that points at nothing, the other refuses to remove
 * what a workflow points at.
 *
 * <p>Rule ids (base-rule) and state machine ids (base-state) are deliberately not checked here —
 * base-workflow-api.yaml records them as-is and validates them lazily at instance start, because
 * this module owns neither registry. The same goes for a start event's {@code milestoneRef} and
 * PPCL expressions; its {@code requiredArtifacts} and {@code authorizedRoles}, however, name this
 * organization's own catalog and so are checked. A TRIGGERING_EVENT's {@code eventType} names
 * base-event's catalog, which this module does not own either — but a misspelt one would make a
 * workflow that never starts and says nothing, so it is asked through {@link EventCatalogPort}. The
 * same port vets each intermediate {@link EventUse}: its definition must exist and, where the port can
 * tell the kind, a THROW may not name a SYSTEM event and only a MESSAGE carries a correlation key.
 *
 * <p>A timer catch names no catalogued event; its {@link TimerDefinition} is checked by
 * {@link TimerExpressions} instead, as far as a literal can be checked before it runs. A boundary event —
 * one {@code attachedTo} a task — is a catch with no {@code dependsOn} of its own, and a CYCLE timer is
 * only allowed on a non-interrupting one: an interrupting timer ends its task at the first firing, and
 * a repeating catch in the flow would have no task whose lifetime bounds it.
 *
 * <p>Tasks, intermediate events and start events share one id namespace, because {@code dependsOn}
 * names tasks and events alike. The flow they form must be acyclic — a cycle would leave every node
 * on it waiting for another forever — which {@link #detectCycles} checks over tasks and events. A
 * boundary event counts as depending on its task.
 */
@Component
public class WorkflowValidator {

    private final RoleDefinitionRepository roleRepository;
    private final ArtifactDefinitionRepository artifactRepository;
    private final ToolDefinitionRepository toolRepository;
    private final TaskDefinitionRepository taskRepository;
    private final EventCatalogPort eventCatalog;

    @Autowired
    public WorkflowValidator(RoleDefinitionRepository roleRepository, ArtifactDefinitionRepository artifactRepository,
                             ToolDefinitionRepository toolRepository, TaskDefinitionRepository taskRepository,
                             ObjectProvider<EventCatalogPort> eventCatalogProvider) {
        this(roleRepository, artifactRepository, toolRepository, taskRepository,
                eventCatalogProvider.getIfUnique(PermitAllEventCatalogPort::new));
    }

    public WorkflowValidator(RoleDefinitionRepository roleRepository, ArtifactDefinitionRepository artifactRepository,
                             ToolDefinitionRepository toolRepository, TaskDefinitionRepository taskRepository,
                             EventCatalogPort eventCatalog) {
        this.roleRepository = roleRepository;
        this.artifactRepository = artifactRepository;
        this.toolRepository = toolRepository;
        this.taskRepository = taskRepository;
        this.eventCatalog = eventCatalog;
    }

    public void validate(Workflow workflow) {
        String orgKey = workflow.getOrgKey();

        List<String> roleIds = workflow.roleDefinitionIds();
        List<String> artifactIds = workflow.artifactDefinitionIds();
        List<String> toolIds = workflow.toolDefinitionIds();
        List<String> taskIds = workflow.taskDefinitionIds();

        requireUnique(roleIds, "role use");
        requireUnique(artifactIds, "artifact use");
        requireUnique(toolIds, "tool use");
        requireUnique(taskIds, "task use");

        roleIds.forEach(roleId -> requireExists(roleRepository.existsByOrgKeyAndId(orgKey, roleId), "role", roleId));
        artifactIds.forEach(artifactId -> requireExists(
                artifactRepository.existsByOrgKeyAndId(orgKey, artifactId), "artifact", artifactId));
        toolIds.forEach(toolId -> requireExists(toolRepository.existsByOrgKeyAndId(orgKey, toolId), "tool", toolId));

        List<String> eventIds = workflow.eventUseIds();
        requireUniqueIds(taskIds, eventIds);
        Set<String> startEventIds = startEventIds(workflow);
        Set<String> flowIds = new HashSet<>(taskIds);
        flowIds.addAll(eventIds);

        validateTaskUses(workflow, orgKey, roleIds, taskIds, flowIds, startEventIds);
        validateEvents(workflow, orgKey, new HashSet<>(taskIds), flowIds, startEventIds);
        validateStartEvents(workflow, orgKey, flowIds);
        detectCycles(workflow);
    }

    private void validateTaskUses(Workflow workflow, String orgKey, List<String> roleIds, List<String> taskIds,
                                  Set<String> flowIds, Set<String> startEventIds) {
        Map<String, TaskDefinition> tasksById = taskRepository
                .findByOrgKeyAndIdIn(orgKey, taskIds)
                .stream()
                .collect(Collectors.toMap(TaskDefinition::getId, Function.identity()));
        Set<String> declaredRoles = new HashSet<>(roleIds);
        Set<String> declaredArtifacts = new HashSet<>(workflow.artifactDefinitionIds());

        for (TaskUse use : workflow.getTasks()) {
            String taskId = use.getTaskDefinitionId();
            TaskDefinition task = tasksById.get(taskId);
            requireExists(task != null, "task", taskId);

            validatePerformedBy(use, task, declaredRoles);
            validateTaskArtifacts(task, declaredArtifacts);
            validateArtifactStates(use, task);
            validateDependsOn("Task", taskId, use.getDependsOn(), flowIds, startEventIds);
        }
    }

    /**
     * The task says who is <em>able</em> to perform it, the workflow says who <em>does</em> — so both
     * have to agree. A role the task does not offer would let a workflow route work to someone the
     * task's author never intended; a role the workflow does not declare would leave the workflow's
     * own role list an incomplete picture of who takes part in it.
     */
    private void validatePerformedBy(TaskUse use, TaskDefinition task, Set<String> declaredRoles) {
        String performedBy = use.getPerformedBy();
        if (performedBy == null || performedBy.isBlank()) {
            throw new ValidationException("Task '%s' has no performedBy role".formatted(use.getTaskDefinitionId()));
        }
        if (!declaredRoles.contains(performedBy)) {
            throw new ValidationException("Task '%s' is performedBy '%s', which the workflow does not declare in roles"
                    .formatted(use.getTaskDefinitionId(), performedBy));
        }
        List<String> capableRoles = task.getPerformedByRoles() == null ? List.of() : task.getPerformedByRoles();
        if (!capableRoles.contains(performedBy)) {
            throw new ValidationException("Task '%s' cannot be performed by '%s' — its performedByRoles are %s"
                    .formatted(use.getTaskDefinitionId(), performedBy, capableRoles));
        }
    }

    /**
     * A task reads and writes artifacts of the organization; the workflow using it has to declare
     * every one of them. Otherwise the workflow's {@code artifacts} would not be the full picture of
     * what flows through it, and an instance would create artifact instances the definition never
     * mentioned.
     */
    private void validateTaskArtifacts(TaskDefinition task, Set<String> declaredArtifacts) {
        for (String artifactId : allArtifactsOf(task)) {
            if (!declaredArtifacts.contains(artifactId)) {
                throw new ValidationException("Task '%s' uses artifact '%s', which the workflow does not declare"
                        .formatted(task.getId(), artifactId));
            }
        }
    }

    /**
     * A state only means something for an artifact the task actually reads or writes, and on the side
     * it does so: an input state on something the task only produces would draw an object nothing
     * delivers. The state names themselves are base-state's and are not resolved here.
     */
    private void validateArtifactStates(TaskUse use, TaskDefinition task) {
        List<TaskArtifactState> states = use.getArtifactStates() == null ? List.of() : use.getArtifactStates();
        List<String> inputs = task.getInputs() == null ? List.of() : task.getInputs();
        List<String> outputs = task.getOutputs() == null ? List.of() : task.getOutputs();
        Set<String> seen = new HashSet<>();
        for (TaskArtifactState state : states) {
            String artifactId = state.getArtifactDefinitionId();
            if (!seen.add(artifactId)) {
                throw new ValidationException("Task '%s' states artifact '%s' twice".formatted(task.getId(), artifactId));
            }
            if (!inputs.contains(artifactId) && !outputs.contains(artifactId)) {
                throw new ValidationException("Task '%s' states artifact '%s', which it neither reads nor writes"
                        .formatted(task.getId(), artifactId));
            }
            if (isSet(state.getInputState()) && !inputs.contains(artifactId)) {
                throw new ValidationException("Task '%s' gives an input state to artifact '%s', which is not one of its inputs"
                        .formatted(task.getId(), artifactId));
            }
            if (isSet(state.getOutputState()) && !outputs.contains(artifactId)) {
                throw new ValidationException("Task '%s' gives an output state to artifact '%s', which is not one of its outputs"
                        .formatted(task.getId(), artifactId));
            }
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> allArtifactsOf(TaskDefinition task) {
        List<String> inputs = task.getInputs() == null ? List.of() : task.getInputs();
        List<String> outputs = task.getOutputs() == null ? List.of() : task.getOutputs();
        return java.util.stream.Stream.concat(inputs.stream(), outputs.stream()).distinct().toList();
    }

    /**
     * A {@code dependsOn} entry, of a task or of an event, names a task or an event of this workflow.
     * Not a start event: the flow already begins at every node without dependencies, and letting one
     * start event gate a node would need the engine to know which start admitted the instance.
     */
    private void validateDependsOn(String ownerKind, String ownerId, List<String> dependsOn, Set<String> flowIds,
                                   Set<String> startEventIds) {
        for (String dependsOnId : dependsOn == null ? List.<String>of() : dependsOn) {
            if (dependsOnId.equals(ownerId)) {
                throw new ValidationException("%s '%s' cannot depend on itself".formatted(ownerKind, ownerId));
            }
            if (startEventIds.contains(dependsOnId)) {
                throw new ValidationException("%s '%s' dependsOn start event '%s'; name a task or an event instead"
                        .formatted(ownerKind, ownerId, dependsOnId));
            }
            if (!flowIds.contains(dependsOnId)) {
                throw new ValidationException("%s '%s' dependsOn '%s', which this workflow does not use"
                        .formatted(ownerKind, ownerId, dependsOnId));
            }
        }
    }

    private void validateEvents(Workflow workflow, String orgKey, Set<String> taskIds, Set<String> flowIds,
                                Set<String> startEventIds) {
        for (EventUse event : workflow.getEvents()) {
            String eventId = event.getId();
            if (event.getDirection() == null) {
                throw new ValidationException("Event '%s' has no direction".formatted(eventId));
            }
            String definitionId = event.getEventDefinitionId();
            if (isSet(definitionId) == event.hasTimer()) {
                throw new ValidationException(
                        "Event '%s' must name either an eventDefinitionId or a timer, and not both".formatted(eventId));
            }
            if (event.hasTimer()) {
                validateTimerEvent(event);
            } else {
                if (!eventCatalog.exists(orgKey, definitionId)) {
                    throw new ValidationException("Event '%s' names event '%s', which is not in this organization's event catalog"
                            .formatted(eventId, definitionId));
                }
                Optional<CatalogEventKind> kind = eventCatalog.kindOf(orgKey, definitionId);
                kind.ifPresent(known -> validateEventKind(event, known));
            }
            if (event.isBoundary()) {
                validateBoundary(event, taskIds);
            }
            validateDependsOn("Event", eventId, event.getDependsOn(), flowIds, startEventIds);
        }
    }

    private void validateTimerEvent(EventUse event) {
        if (!event.isCatch()) {
            throw new ValidationException("Event '%s' has a timer, so it must be a CATCH".formatted(event.getId()));
        }
        if (isSet(event.getCorrelationKey())) {
            throw new ValidationException("Event '%s' is a timer; only a MESSAGE takes a correlationKey".formatted(event.getId()));
        }
        validateTimer("Event", event.getId(), event.getTimer());
        if (event.getTimer().getType() == TimerType.CYCLE && !(event.isBoundary() && !event.isInterrupting())) {
            throw new ValidationException(
                    "Event '%s' has a CYCLE timer, which only a non-interrupting boundary event may have".formatted(event.getId()));
        }
    }

    private void validateBoundary(EventUse event, Set<String> taskIds) {
        if (!taskIds.contains(event.getAttachedTo())) {
            throw new ValidationException("Event '%s' is attachedTo '%s', which is not a task of this workflow"
                    .formatted(event.getId(), event.getAttachedTo()));
        }
        if (!event.isCatch()) {
            throw new ValidationException("Boundary event '%s' must be a CATCH".formatted(event.getId()));
        }
        if (!orEmpty(event.getDependsOn()).isEmpty()) {
            throw new ValidationException(
                    "Boundary event '%s' takes no dependsOn — it is reached when its task becomes ACTIVE".formatted(event.getId()));
        }
    }

    private static void validateTimer(String ownerKind, String ownerId, TimerDefinition timer) {
        try {
            TimerExpressions.validate(timer);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("%s '%s' has an invalid timer: %s".formatted(ownerKind, ownerId, e.getMessage()));
        }
    }

    private void validateEventKind(EventUse event, CatalogEventKind kind) {
        if (event.isThrow() && kind == CatalogEventKind.SYSTEM) {
            throw new ValidationException("Event '%s' throws SYSTEM event '%s', which only the platform raises"
                    .formatted(event.getId(), event.getEventDefinitionId()));
        }
        boolean hasKey = isSet(event.getCorrelationKey());
        if (kind == CatalogEventKind.MESSAGE && !hasKey) {
            throw new ValidationException("Event '%s' names MESSAGE '%s' but has no correlationKey"
                    .formatted(event.getId(), event.getEventDefinitionId()));
        }
        if (kind != CatalogEventKind.MESSAGE && hasKey) {
            throw new ValidationException("Event '%s' names %s '%s'; only a MESSAGE takes a correlationKey"
                    .formatted(event.getId(), kind, event.getEventDefinitionId()));
        }
    }

    /**
     * Depth-first search over the {@code dependsOn} edges of tasks and events. A node on the current
     * path seen again closes a cycle; the message spells the cycle out.
     */
    private void detectCycles(Workflow workflow) {
        Map<String, List<String>> edges = new LinkedHashMap<>();
        workflow.getTasks().forEach(use -> edges.put(use.getTaskDefinitionId(), orEmpty(use.getDependsOn())));
        workflow.getEvents().forEach(use -> edges.put(use.getId(),
                use.isBoundary() ? List.of(use.getAttachedTo()) : orEmpty(use.getDependsOn())));
        Map<String, Boolean> done = new HashMap<>();
        for (String node : edges.keySet()) {
            visit(node, edges, done, new ArrayList<>());
        }
    }

    private void visit(String node, Map<String, List<String>> edges, Map<String, Boolean> done, List<String> path) {
        if (Boolean.TRUE.equals(done.get(node))) {
            return;
        }
        int onPath = path.indexOf(node);
        if (onPath >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(onPath, path.size()));
            cycle.add(node);
            throw new ValidationException("The flow has a cycle: %s".formatted(String.join(" -> ", cycle)));
        }
        path.add(node);
        for (String next : edges.getOrDefault(node, List.of())) {
            visit(next, edges, done, path);
        }
        path.remove(path.size() - 1);
        done.put(node, true);
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static Set<String> startEventIds(Workflow workflow) {
        Set<String> ids = new HashSet<>();
        orEmptyStartEvents(workflow).forEach(startEvent -> ids.add(startEvent.getId()));
        return ids;
    }

    private static List<StartEvent> orEmptyStartEvents(Workflow workflow) {
        return workflow.getStartEvents() == null ? List.of() : workflow.getStartEvents();
    }

    /** Task and event ids share one namespace, because {@code dependsOn} names both. */
    private void requireUniqueIds(List<String> taskIds, List<String> eventIds) {
        Set<String> seen = new HashSet<>();
        for (String eventId : eventIds) {
            if (!isSet(eventId)) {
                throw new ValidationException("An event has no id");
            }
            if (!seen.add(eventId)) {
                throw new ValidationException("Duplicate event '%s' within workflow".formatted(eventId));
            }
            if (taskIds.contains(eventId)) {
                throw new ValidationException("Event '%s' has the same id as a task of the workflow".formatted(eventId));
            }
        }
    }

    private void validateStartEvents(Workflow workflow, String orgKey, Set<String> flowIds) {
        List<StartEvent> startEvents = orEmptyStartEvents(workflow);
        Set<String> seenIds = new HashSet<>();
        Set<String> declaredArtifacts = new HashSet<>(workflow.artifactDefinitionIds());
        for (StartEvent startEvent : startEvents) {
            validateStartEventIdentity(startEvent, seenIds, flowIds);
            validateStartEventArtifacts(startEvent, orgKey, declaredArtifacts);

            List<String> authorizedRoles =
                    startEvent.getAuthorizedRoles() == null ? List.of() : startEvent.getAuthorizedRoles();
            for (String roleId : authorizedRoles) {
                requireExists(roleRepository.existsByOrgKeyAndId(orgKey, roleId), "role", roleId);
            }

            if (startEvent.getStartType() == WorkflowStartConditionType.TRIGGERING_EVENT) {
                validateEventType(orgKey, startEvent);
            }
            if (startEvent.getStartType() == WorkflowStartConditionType.TIME_BASED_PRECONDITION) {
                validateStartTimer(startEvent);
            }
        }
    }

    private void validateStartEventIdentity(StartEvent startEvent, Set<String> seenIds, Set<String> flowIds) {
        String eventId = startEvent.getId();
        if (eventId == null || eventId.isBlank()) {
            throw new ValidationException("A start event has no id");
        }
        if (!seenIds.add(eventId)) {
            throw new ValidationException("Duplicate start event '%s' within workflow".formatted(eventId));
        }
        if (flowIds.contains(eventId)) {
            throw new ValidationException(
                    "Start event '%s' has the same id as a task or event of the workflow".formatted(eventId));
        }
        if (startEvent.getStartType() == null) {
            throw new ValidationException("Start event '%s' has no startType".formatted(eventId));
        }
    }

    private void validateStartEventArtifacts(StartEvent startEvent, String orgKey, Set<String> declaredArtifacts) {
        List<RequiredStartArtifact> required =
                startEvent.getRequiredArtifacts() == null ? List.of() : startEvent.getRequiredArtifacts();
        for (RequiredStartArtifact artifact : required) {
            String artifactId = artifact.getArtifactDefinitionId();
            requireExists(artifactRepository.existsByOrgKeyAndId(orgKey, artifactId), "artifact", artifactId);
            if (!declaredArtifacts.contains(artifactId)) {
                throw new ValidationException("Start event '%s' requires artifact '%s', which the workflow does not declare"
                        .formatted(startEvent.getId(), artifactId));
            }
        }
    }

    /**
     * A scheduled start needs a moment to start at: a DATE or a CYCLE, as a literal. A DURATION would have
     * nothing to be relative to, and a path nothing to read — there is no instance yet.
     */
    private static void validateStartTimer(StartEvent startEvent) {
        TimerDefinition timer = startEvent.getTimer();
        if (timer == null) {
            throw new ValidationException(
                    "Start event '%s' is a TIME_BASED_PRECONDITION but has no timer".formatted(startEvent.getId()));
        }
        validateTimer("Start event", startEvent.getId(), timer);
        if (timer.getType() == TimerType.DURATION) {
            throw new ValidationException("Start event '%s' has a DURATION timer; a start needs a DATE or a CYCLE"
                    .formatted(startEvent.getId()));
        }
        if (timer.isPath()) {
            throw new ValidationException("Start event '%s' has a timer path; a start has no context to read it from"
                    .formatted(startEvent.getId()));
        }
    }

    private void validateEventType(String orgKey, StartEvent startEvent) {
        String eventType = startEvent.getEventType();
        if (eventType == null || eventType.isBlank()) {
            throw new ValidationException(
                    "Start event '%s' is a TRIGGERING_EVENT but names no eventType".formatted(startEvent.getId()));
        }
        if (!eventCatalog.exists(orgKey, eventType)) {
            throw new ValidationException("Start event '%s' names event '%s', which is not in this organization's event catalog"
                    .formatted(startEvent.getId(), eventType));
        }
    }

    private void requireExists(boolean exists, String kind, String id) {
        if (!exists) {
            throw new ValidationException("No %s definition with id '%s' in this organization".formatted(kind, id));
        }
    }

    private void requireUnique(List<String> ids, String kind) {
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                throw new ValidationException("Duplicate %s '%s' within workflow".formatted(kind, id));
            }
        }
    }
}
