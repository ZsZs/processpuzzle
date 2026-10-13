package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.ToolUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.domain.event.WorkflowChangedEvent;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Releases workflows, each together with its diagram: the layout is part of what the designer releases, as a state
 * machine's is, so a moved node is a pending change too, and the released diagram is the one the running
 * application's monitoring draws.
 *
 * <p>A workflow refers to the workflow it extends, the roles, artifacts, tools and tasks it uses, the roles that
 * perform its tasks and may start it, the artifacts a start requires, and the events it is started by, throws and
 * waits for.
 *
 * <p><strong>What it refuses.</strong> Removing a workflow while instances of it run: they could neither progress nor
 * finish. A released workflow's start timers follow the release, not the draft: the release announces
 * {@link WorkflowChangedEvent}, which reschedules them after it commits.
 */
@Component
public class WorkflowReleaseParticipant extends DefinitionReleaseParticipant<Workflow> {

    /** The statuses of an instance that still runs. */
    static final Set<WorkflowInstanceStatus> RUNNING = EnumSet.of(WorkflowInstanceStatus.ACTIVE, WorkflowInstanceStatus.SUSPENDED);

    private final WorkflowRepository repository;
    private final WorkflowDiagramRepository diagrams;
    private final WorkflowInstanceRepository instances;
    private final ApplicationEventPublisher events;

    public WorkflowReleaseParticipant(WorkflowRepository repository, WorkflowDiagramRepository diagrams,
                                      WorkflowInstanceRepository instances, ApplicationEventPublisher events) {
        super(DefinitionKinds.WORKFLOW, DefinitionKinds.WORKFLOW_ORDER, repository);
        this.repository = repository;
        this.diagrams = diagrams;
        this.instances = instances;
        this.events = events;
    }

    @Override
    protected List<Workflow> rows(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    @Override
    protected String keyOf(Workflow row) {
        return row.getId();
    }

    @Override
    protected Map<String, Object> contentOf(Workflow row) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", row.getName());
        content.put("description", row.getDescription());
        content.put("extendsWorkflowId", row.getExtendsWorkflowId());
        content.put("startEvents", row.getStartEvents());
        content.put("roles", row.getRoles());
        content.put("artifacts", row.getArtifacts());
        content.put("tools", row.getTools());
        content.put("tasks", row.getTasks());
        content.put("events", row.getEvents());
        diagrams.findByOrgKeyAndWorkflowIdAndChannel(row.getOrgKey(), row.getId(), row.getChannel()).ifPresent(diagram -> {
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("nodes", diagram.getNodes());
            layout.put("edges", diagram.getEdges());
            layout.put("viewport", diagram.getViewport());
            content.put("diagram", layout);
        });
        return content;
    }

    @Override
    protected Workflow blankLike(Workflow draft) {
        return Workflow.builder().orgKey(draft.getOrgKey()).id(draft.getId()).build();
    }

    @Override
    protected void copy(Workflow draft, Workflow released) {
        released.setName(draft.getName());
        released.setDescription(draft.getDescription());
        released.setExtendsWorkflowId(draft.getExtendsWorkflowId());
        released.setStartEvents(TaskReleaseParticipant.copyOf(draft.getStartEvents()));
        released.setRoles(TaskReleaseParticipant.copyOf(draft.getRoles()));
        released.setArtifacts(TaskReleaseParticipant.copyOf(draft.getArtifacts()));
        released.setTools(TaskReleaseParticipant.copyOf(draft.getTools()));
        released.setTasks(TaskReleaseParticipant.copyOf(draft.getTasks()));
        released.setEvents(TaskReleaseParticipant.copyOf(draft.getEvents()));
    }

    @Override
    protected List<DefinitionReference> referencesOf(Workflow draft) {
        List<DefinitionReference> references = new ArrayList<>();
        add(references, draft, DefinitionKinds.WORKFLOW, Stream.of(draft.getExtendsWorkflowId()));
        add(references, draft, DefinitionKinds.ROLE, keys(draft.getRoles(), RoleUse::getRoleDefinitionId));
        add(references, draft, DefinitionKinds.ARTIFACT, keys(draft.getArtifacts(), ArtifactUse::getArtifactDefinitionId));
        add(references, draft, DefinitionKinds.TOOL, keys(draft.getTools(), ToolUse::getToolDefinitionId));
        add(references, draft, DefinitionKinds.TASK, keys(draft.getTasks(), TaskUse::getTaskDefinitionId));
        add(references, draft, DefinitionKinds.ROLE, keys(draft.getTasks(), TaskUse::getPerformedBy));
        add(references, draft, DefinitionKinds.EVENT, keys(draft.getEvents(), EventUse::getEventDefinitionId));
        for (StartEvent start : nonNull(draft.getStartEvents())) {
            if (start.getStartType() == WorkflowStartConditionType.TRIGGERING_EVENT) {
                add(references, draft, DefinitionKinds.EVENT, Stream.of(start.getEventType()));
            }
            add(references, draft, DefinitionKinds.ROLE, nonNull(start.getAuthorizedRoles()).stream());
            add(references, draft, DefinitionKinds.ARTIFACT,
                    keys(start.getRequiredArtifacts(), RequiredStartArtifact::getArtifactDefinitionId));
        }
        return references.stream().distinct().toList();
    }

    @Override
    protected List<ReleaseProblem> problemsOf(String orgKey, Map<String, Workflow> drafts, Map<String, Workflow> released) {
        return released.keySet().stream()
                .filter(workflowId -> !drafts.containsKey(workflowId))
                .filter(workflowId -> instances.existsByOrgKeyAndWorkflowIdAndStatusIn(orgKey, workflowId, RUNNING))
                .map(workflowId -> ReleaseProblem.error(kind(), workflowId, "workflow.release.workflowRemovedWhileRunning",
                        "The workflow '%s' is removed, but instances of it are still running.".formatted(workflowId)))
                .toList();
    }

    @Override
    protected void released(String orgKey, List<Workflow> releasedRows, Set<String> removedKeys) {
        for (Workflow workflow : releasedRows) {
            releaseDiagram(orgKey, workflow.getId());
            events.publishEvent(new WorkflowChangedEvent(orgKey, workflow.getId(), false));
        }
        for (String workflowId : removedKeys) {
            diagrams.deleteByOrgKeyAndWorkflowIdAndChannel(orgKey, workflowId, Channel.RELEASED);
            events.publishEvent(new WorkflowChangedEvent(orgKey, workflowId, true));
        }
    }

    /** The draft's layout onto the released one, or none when the draft has none. */
    private void releaseDiagram(String orgKey, String workflowId) {
        WorkflowDiagram draft = diagrams.findByOrgKeyAndWorkflowIdAndChannel(orgKey, workflowId, Channel.DRAFT).orElse(null);
        WorkflowDiagram current = diagrams.findByOrgKeyAndWorkflowIdAndChannel(orgKey, workflowId, Channel.RELEASED).orElse(null);
        if (draft == null) {
            if (current != null) {
                diagrams.delete(current);
            }
            return;
        }
        if (current == null) {
            current = WorkflowDiagram.builder().orgKey(orgKey).workflowId(workflowId).build();
            current.setChannel(Channel.RELEASED);
        }
        current.replaceLayout(TaskReleaseParticipant.copyOf(draft.getNodes()), TaskReleaseParticipant.copyOf(draft.getEdges()),
                draft.getViewport());
        diagrams.save(current);
    }

    private void add(List<DefinitionReference> references, Workflow draft, String toKind, Stream<String> keys) {
        keys.filter(ArtifactReleaseParticipant::isNamed)
                .forEach(key -> references.add(DefinitionReference.required(kind(), draft.getId(), toKind, key)));
    }

    private static <T> Stream<String> keys(List<T> uses, Function<T, String> key) {
        return nonNull(uses).stream().map(key);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
