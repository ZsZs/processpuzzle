package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.shared.event.EventThrown;
import com.processpuzzle.workflow.definition.domain.EventUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow.ResolvedTask;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.PayloadPath;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.events.TaskActivatedEvent;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleEvaluationPort;
import com.processpuzzle.workflow.definition.domain.TimerExpressions;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The workflow engine's core: decides which PENDING/BLOCKED tasks of a running workflow instance
 * are eligible to become ACTIVE right now, and evaluates each one's precondition rule; and reaches
 * the intermediate events whose dependencies are done — a THROW is raised at once, a CATCH starts
 * waiting. Called after every state-changing event within a workflow instance (start, task
 * completion, task skip, a caught event) so the workflow keeps advancing on its own.
 *
 * <p>It works against a {@link ResolvedWorkflow} rather than a {@code Workflow} because the wiring it
 * reads — {@code dependsOn}, {@code joinType}, {@code parallel} — lives on the workflow's
 * {@code TaskUse}, while the precondition rule belongs to the shared task definition; the resolved
 * view is where the two are already paired up.
 *
 * <p><b>Ordering within a dependency level:</b> a task is only a <em>candidate</em> once its
 * {@code dependsOn} is satisfied — a task COMPLETED or SKIPPED, an event THROWN or OCCURRED. Among
 * candidate tasks that share the exact same {@code dependsOn} set (i.e. the same "level" of the
 * graph), {@code parallel == false} (the default) tasks run one at a time in workflow-definition
 * order: a non-parallel candidate only attempts activation once no earlier sibling at that level is
 * still ACTIVE or BLOCKED. {@code parallel == true} tasks skip that check and may all activate
 * together. This is a reasonable, defensible reading of the contract rather than a literal spec
 * requirement — the API description only says parallel "can run concurrently with its siblings that
 * share the same dependsOn", it doesn't fully specify non-parallel ordering, so this fills the gap the
 * way SPEM's own informal-sequencing intent suggests. Events take no part in the sibling rule.
 *
 * <p><b>Events, to a fixed point.</b> A THROW is done the moment it is reached, so raising one can
 * satisfy further tasks and events within the same call. The service therefore repeats its pass
 * until no throw fires. That terminates: nodes only ever move forward, and only a throw can satisfy
 * a dependency within a pass, so there are at most one pass per throw plus a final quiet one. Catch
 * events that every dependent has already moved past — an ANY-join that went ahead on another
 * branch — are withdrawn (CANCELLED) afterwards, so they stop waiting and no longer hold the instance
 * open.
 *
 * <p><b>Timers and boundary events.</b> A timer catch, once reached, waits with a {@code dueAt} that
 * {@code TimerSweep} polls for. A boundary event is not reached through {@code dependsOn}: each pass,
 * after events and tasks, arms the PENDING boundaries whose task is now ACTIVE.
 *
 * <p><b>Dead-path elimination.</b> A task or event that can no longer be reached is cancelled rather
 * than left PENDING, where it would hold the instance open forever. A node is dead when its join is ALL
 * and any dependency is CANCELLED, or its join is ANY and every dependency is. A boundary whose task
 * has ended is cancelled first, which is what makes a boundary's own successors dead when the task
 * finished normally, and an interrupted task's successors dead when it did not. This runs to a fixed
 * point at the start of every pass and once more at the end.
 *
 * <p>The event rows are reconciled with the live definition first: an {@code EventUse} added since
 * the run started gets a PENDING row, the same way the definition is read live for tasks.
 */
@Service
public class TaskActivationService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskActivationService.class);
    private static final Set<TaskInstanceStatus> TERMINAL_TASK =
            EnumSet.of(TaskInstanceStatus.COMPLETED, TaskInstanceStatus.SKIPPED, TaskInstanceStatus.CANCELLED);
    private static final Set<TaskInstanceStatus> UNSTARTED_TASK =
            EnumSet.of(TaskInstanceStatus.PENDING, TaskInstanceStatus.BLOCKED);
    static final String UNREACHABLE = "unreachable";
    private static final Set<EventInstanceStatus> OPEN_EVENT =
            EnumSet.of(EventInstanceStatus.PENDING, EventInstanceStatus.WAITING);

    private final TaskInstanceRepository taskInstanceRepository;
    private final EventInstanceRepository eventInstanceRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final RuleEvaluationPort ruleEvaluationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public TaskActivationService(TaskInstanceRepository taskInstanceRepository,
                                 EventInstanceRepository eventInstanceRepository,
                                 WorkflowInstanceRepository workflowInstanceRepository,
                                 RuleEvaluationPort ruleEvaluationPort,
                                 ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.ruleEvaluationPort = ruleEvaluationPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public TaskActivationService(TaskInstanceRepository taskInstanceRepository,
                                 EventInstanceRepository eventInstanceRepository,
                                 WorkflowInstanceRepository workflowInstanceRepository,
                                 RuleEvaluationPort ruleEvaluationPort,
                                 ApplicationEventPublisher eventPublisher) {
        this(taskInstanceRepository, eventInstanceRepository, workflowInstanceRepository, ruleEvaluationPort,
                eventPublisher, Clock.systemUTC());
    }

    /**
     * Re-evaluates every non-terminal task and event of {@code workflowInstanceId}, activates the
     * tasks now eligible and reaches the events now due. Idempotent: calling it repeatedly with no
     * state change is a no-op.
     */
    public void activateEligibleTasks(String orgKey, ResolvedWorkflow workflow,
                                      UUID workflowInstanceId, Map<String, Object> context) {
        List<TaskInstance> tasks = taskInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
        List<EventInstance> events = workflow.events().isEmpty()
                ? List.of()
                : reconcileEvents(orgKey, workflow, workflowInstanceId);
        FlowState state = new FlowState(tasks, events);
        Run run = new Run(orgKey, workflow, workflowInstanceId, context, state);

        long throwCount = workflow.events().stream().filter(EventUse::isThrow).count();
        for (long pass = 0; pass <= throwCount; pass++) {
            eliminateDeadPaths(run);
            boolean thrown = reachDueEvents(run);
            for (ResolvedTask task : workflow.tasks()) {
                activateTaskIfEligible(run, task);
            }
            armBoundaries(run);
            if (!thrown) {
                break;
            }
        }
        eliminateDeadPaths(run);
        withdrawObsoleteCatches(run);
    }

    // ---------------------------------------------------------------- events

    /** One row per {@code EventUse} of the live definition; missing ones are created PENDING. */
    private List<EventInstance> reconcileEvents(String orgKey, ResolvedWorkflow workflow, UUID workflowInstanceId) {
        List<EventInstance> events = new ArrayList<>(
                eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId));
        Set<String> present = events.stream().map(EventInstance::getEventUseId).collect(Collectors.toSet());
        for (EventUse use : workflow.events()) {
            if (!present.contains(use.getId())) {
                events.add(eventInstanceRepository.save(pendingEventInstance(orgKey, workflowInstanceId, use)));
            }
        }
        return events;
    }

    /** A not-yet-reached row for {@code use}; also what a new instance starts with. */
    static EventInstance pendingEventInstance(String orgKey, UUID workflowInstanceId, EventUse use) {
        return EventInstance.builder()
                .orgKey(orgKey)
                .workflowInstanceId(workflowInstanceId)
                .eventUseId(use.getId())
                .eventDefinitionId(use.getEventDefinitionId())
                .name(use.getName())
                .direction(use.getDirection())
                .status(EventInstanceStatus.PENDING)
                .build();
    }

    /**
     * Reaches every PENDING event in the flow whose dependencies are done; boundaries are armed by
     * {@link #armBoundaries} instead. @return whether a throw fired
     */
    private boolean reachDueEvents(Run run) {
        boolean thrown = false;
        for (EventUse use : run.workflow.events()) {
            EventInstance event = run.state.event(use.getId());
            if (event == null || use.isBoundary() || event.getStatus() != EventInstanceStatus.PENDING
                    || !run.state.isSatisfied(use.getDependsOn(), use.getJoinType())) {
                continue;
            }
            if (use.isThrow()) {
                raise(run, use, event);
                thrown = true;
            } else {
                startWaiting(run, use, event);
            }
        }
        return thrown;
    }

    /** A PENDING boundary starts waiting once its task is ACTIVE. */
    private void armBoundaries(Run run) {
        for (EventUse use : run.workflow.events()) {
            EventInstance event = run.state.event(use.getId());
            TaskInstance task = use.isBoundary() ? run.state.task(use.getAttachedTo()) : null;
            if (event != null && task != null && event.getStatus() == EventInstanceStatus.PENDING
                    && task.getStatus() == TaskInstanceStatus.ACTIVE) {
                startWaiting(run, use, event);
            }
        }
    }

    private void raise(Run run, EventUse use, EventInstance event) {
        WorkflowInstance instance = run.instance();
        Instant now = clock.instant();
        Map<String, Object> payload = PayloadPath.map(run.context, use.getPayloadMapping());
        String correlationValue = use.getCorrelationKey() == null ? null : stringOf(run.context.get(use.getCorrelationKey()));
        event.setStatus(EventInstanceStatus.THROWN);
        event.setCorrelationValue(correlationValue);
        event.setPayload(payload);
        event.setOccurredAt(now);
        event.setOccurrenceId(event.getId());
        eventInstanceRepository.save(event);
        eventPublisher.publishEvent(new EventThrown(run.orgKey, use.getEventDefinitionId(), event.getId(),
                instance == null ? null : instance.getEntityType(), instance == null ? null : instance.getEntityId(),
                correlationValue, payload, run.workflowInstanceId, run.workflow.id(), now));
    }

    /**
     * A MESSAGE catch waits for its correlation variable's value. Any other catch waits for the
     * instance's subject: a SYSTEM event is matched on it, and for a SIGNAL it is merely recorded.
     */
    private void startWaiting(Run run, EventUse use, EventInstance event) {
        if (use.hasTimer()) {
            armTimer(run, use, event);
            return;
        }
        String correlationValue;
        if (use.getCorrelationKey() != null) {
            correlationValue = stringOf(run.context.get(use.getCorrelationKey()));
        } else {
            WorkflowInstance instance = run.instance();
            correlationValue = instance == null ? null : instance.getEntityId();
        }
        event.setStatus(EventInstanceStatus.WAITING);
        event.setWaitingSince(clock.instant());
        event.setCorrelationValue(correlationValue);
        eventInstanceRepository.save(event);
    }

    /**
     * A timer catch waits for its due time. One whose expression cannot be armed (a {@code $.} path that
     * resolves to nothing, or to something that is not a time) waits without one, which is logged: it
     * will not fire, and only cancelling the instance releases it.
     */
    private void armTimer(Run run, EventUse use, EventInstance event) {
        Instant now = clock.instant();
        TimerExpressions.Arming arming = null;
        try {
            arming = TimerExpressions.arm(use.getTimer(), path -> PayloadPath.resolve(run.context, path), now).orElse(null);
        } catch (IllegalArgumentException e) {
            LOG.warn("{}: timer '{}' of workflow instance {} cannot be armed: {}", run.orgKey, use.getId(),
                    run.workflowInstanceId, e.getMessage());
        }
        if (arming == null) {
            LOG.warn("{}: timer '{}' of workflow instance {} has no due time ('{}'); it will not fire.", run.orgKey,
                    use.getId(), run.workflowInstanceId, use.getTimer().getExpression());
        }
        event.setStatus(EventInstanceStatus.WAITING);
        event.setWaitingSince(now);
        event.setDueAt(arming == null ? null : arming.dueAt());
        event.setRemainingFirings(arming == null ? null : arming.remainingFirings());
        event.setFireCount(0);
        eventInstanceRepository.save(event);
    }

    // ---------------------------------------------------------------- dead paths

    /**
     * Cancels, to a fixed point, the boundaries whose task has ended and the tasks and events that can no
     * longer be reached. See the class comment.
     */
    private void eliminateDeadPaths(Run run) {
        boolean changed = true;
        while (changed) {
            boolean boundariesCancelled = cancelEndedBoundaries(run);
            boolean tasksCancelled = cancelDeadTasks(run);
            boolean eventsCancelled = cancelDeadEvents(run);
            changed = boundariesCancelled || tasksCancelled || eventsCancelled;
        }
    }

    private boolean cancelEndedBoundaries(Run run) {
        boolean changed = false;
        for (EventUse use : run.workflow.events()) {
            EventInstance event = use.isBoundary() ? run.state.event(use.getId()) : null;
            TaskInstance task = event == null ? null : run.state.task(use.getAttachedTo());
            if (event == null || (task != null && !TERMINAL_TASK.contains(task.getStatus()))) {
                continue;
            }
            if (!event.getStatus().isTerminal()) {
                cancel(event);
                changed = true;
            } else if (event.getDueAt() != null) {
                // A cycle that already fired: it stops with its task.
                event.setDueAt(null);
                eventInstanceRepository.save(event);
            }
        }
        return changed;
    }

    private boolean cancelDeadTasks(Run run) {
        boolean changed = false;
        for (ResolvedTask task : run.workflow.tasks()) {
            TaskInstance instance = run.state.task(task.id());
            if (instance != null && UNSTARTED_TASK.contains(instance.getStatus())
                    && run.state.isDead(task.dependsOn(), task.joinType())) {
                instance.setStatus(TaskInstanceStatus.CANCELLED);
                instance.setCancelledAt(clock.instant());
                instance.setCancelReason(UNREACHABLE);
                taskInstanceRepository.save(instance);
                changed = true;
            }
        }
        return changed;
    }

    private boolean cancelDeadEvents(Run run) {
        boolean changed = false;
        for (EventUse use : run.workflow.events()) {
            EventInstance event = run.state.event(use.getId());
            if (event != null && !use.isBoundary() && event.getStatus() == EventInstanceStatus.PENDING
                    && run.state.isDead(use.getDependsOn(), use.getJoinType())) {
                cancel(event);
                changed = true;
            }
        }
        return changed;
    }

    private void cancel(EventInstance event) {
        event.setStatus(EventInstanceStatus.CANCELLED);
        event.setDueAt(null);
        eventInstanceRepository.save(event);
    }

    /**
     * A catch still PENDING or WAITING whose dependents have all moved past PENDING can no longer
     * matter — they went ahead without it, typically through an ANY-join — so it is withdrawn. A
     * catch nothing depends on is never withdrawn: waiting for it is the whole point.
     */
    private void withdrawObsoleteCatches(Run run) {
        for (EventUse use : run.workflow.events()) {
            EventInstance event = run.state.event(use.getId());
            if (event == null || !use.isCatch()
                    || (event.getStatus() != EventInstanceStatus.PENDING && event.getStatus() != EventInstanceStatus.WAITING)) {
                continue;
            }
            List<Boolean> dependentsMovedOn = dependentProgress(run, use);
            if (!dependentsMovedOn.isEmpty() && dependentsMovedOn.stream().allMatch(Boolean::booleanValue)) {
                cancel(event);
            }
        }
    }

    private List<Boolean> dependentProgress(Run run, EventUse use) {
        List<Boolean> dependentsMovedOn = new ArrayList<>();
        for (ResolvedTask task : run.workflow.tasks()) {
            if (task.dependsOn().contains(use.getId())) {
                TaskInstance dependent = run.state.task(task.id());
                dependentsMovedOn.add(dependent != null && dependent.getStatus() != TaskInstanceStatus.PENDING);
            }
        }
        for (EventUse other : run.workflow.events()) {
            if (other.getDependsOn() != null && other.getDependsOn().contains(use.getId())) {
                EventInstance dependent = run.state.event(other.getId());
                dependentsMovedOn.add(dependent != null && dependent.getStatus() != EventInstanceStatus.PENDING);
            }
        }
        return dependentsMovedOn;
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    // ---------------------------------------------------------------- tasks

    private void activateTaskIfEligible(Run run, ResolvedTask task) {
        TaskInstance instance = run.state.task(task.id());
        if (instance == null || (instance.getStatus() != TaskInstanceStatus.PENDING
                && instance.getStatus() != TaskInstanceStatus.BLOCKED)) {
            return;
        }

        if (!run.state.isSatisfied(task.dependsOn(), task.joinType())) {
            return;
        }

        if (!task.parallel() && hasActiveSiblingAtSameLevel(task, run.workflow, run.state)) {
            return;
        }

        RuleCheckResult check = ruleEvaluationPort.evaluate(run.orgKey, task.definition().getPreconditionRuleId(), run.context);
        if (check.passed()) {
            instance.setStatus(TaskInstanceStatus.ACTIVE);
            instance.setActivatedAt(clock.instant());
            instance.setBlockedReason(null);
            taskInstanceRepository.save(instance);
            eventPublisher.publishEvent(new TaskActivatedEvent(run.orgKey, run.workflowInstanceId, instance.getId(), task.id()));
        } else {
            instance.setStatus(TaskInstanceStatus.BLOCKED);
            instance.setBlockedReason(check.detail());
            taskInstanceRepository.save(instance);
        }
    }

    private boolean hasActiveSiblingAtSameLevel(ResolvedTask task, ResolvedWorkflow workflow, FlowState state) {
        return workflow.tasks().stream()
                .filter(sibling -> !sibling.id().equals(task.id()))
                .filter(sibling -> sibling.dependsOn().equals(task.dependsOn()))
                .filter(sibling -> !sibling.parallel())
                .map(sibling -> state.task(sibling.id()))
                .anyMatch(siblingInstance -> siblingInstance != null
                        && (siblingInstance.getStatus() == TaskInstanceStatus.ACTIVE
                            || siblingInstance.getStatus() == TaskInstanceStatus.BLOCKED));
    }

    // ---------------------------------------------------------------- close-out

    /**
     * Whether nothing of the instance can move any more: every task COMPLETED, SKIPPED or CANCELLED and
     * every event THROWN, OCCURRED or CANCELLED. An event row whose {@code EventUse} the live definition no
     * longer has is ignored — nothing would ever reach it.
     *
     * <p>Counts rather than loaded rows: a row this transaction already holds would be returned as its
     * cached copy, and {@link WorkflowProgression}'s close-out has to see what another transaction
     * committed meanwhile.
     */
    public boolean allTerminal(String orgKey, ResolvedWorkflow workflow, UUID workflowInstanceId) {
        boolean tasksTerminal = taskInstanceRepository.countByOrgKeyAndWorkflowInstanceIdAndStatusNotIn(
                orgKey, workflowInstanceId, TERMINAL_TASK) == 0;
        if (!tasksTerminal || workflow.events().isEmpty()) {
            return tasksTerminal;
        }
        return eventInstanceRepository.countByOrgKeyAndWorkflowInstanceIdAndEventUseIdInAndStatusIn(
                orgKey, workflowInstanceId, workflow.definition().eventUseIds(), OPEN_EVENT) == 0;
    }

    // ---------------------------------------------------------------- state

    /** The task and event rows of one instance, by id, as this call mutates them. */
    static final class FlowState {

        private final Map<String, TaskInstance> tasks = new LinkedHashMap<>();
        private final Map<String, EventInstance> events = new LinkedHashMap<>();

        FlowState(List<TaskInstance> tasks, List<EventInstance> events) {
            tasks.forEach(task -> this.tasks.put(task.getTaskDefinitionId(), task));
            events.forEach(event -> this.events.put(event.getEventUseId(), event));
        }

        TaskInstance task(String id) {
            return tasks.get(id);
        }

        EventInstance event(String id) {
            return events.get(id);
        }

        /** Whether the task or event {@code id} is done: COMPLETED / SKIPPED, or THROWN / OCCURRED. */
        boolean isSatisfied(String id) {
            TaskInstance task = tasks.get(id);
            if (task != null) {
                return task.getStatus() == TaskInstanceStatus.COMPLETED || task.getStatus() == TaskInstanceStatus.SKIPPED;
            }
            EventInstance event = events.get(id);
            return event != null && event.getStatus().isDone();
        }

        /**
         * ALL (the default) waits for every named node, ANY for the first of them. An empty
         * {@code dependsOn} is satisfied under either, which is what makes a node with no dependencies
         * eligible from workflow start: {@code allMatch} over nothing is true, and the ANY branch checks
         * for emptiness explicitly rather than letting {@code anyMatch} return false.
         */
        boolean isSatisfied(List<String> dependsOn, JoinType joinType) {
            if (dependsOn == null || dependsOn.isEmpty()) {
                return true;
            }
            Predicate<String> done = this::isSatisfied;
            return joinType == JoinType.ANY ? dependsOn.stream().anyMatch(done) : dependsOn.stream().allMatch(done);
        }

        /** Whether the task or event {@code id} was cancelled: it will never be done. */
        boolean isCancelled(String id) {
            TaskInstance task = tasks.get(id);
            if (task != null) {
                return task.getStatus() == TaskInstanceStatus.CANCELLED;
            }
            EventInstance event = events.get(id);
            return event != null && event.getStatus() == EventInstanceStatus.CANCELLED;
        }

        /**
         * Whether a node waiting on {@code dependsOn} can never be reached: under ALL one cancelled
         * dependency is enough, under ANY every one has to be. A node without dependencies never is.
         */
        boolean isDead(List<String> dependsOn, JoinType joinType) {
            if (dependsOn == null || dependsOn.isEmpty()) {
                return false;
            }
            Predicate<String> cancelled = this::isCancelled;
            return joinType == JoinType.ANY ? dependsOn.stream().allMatch(cancelled) : dependsOn.stream().anyMatch(cancelled);
        }
    }

    /** What one call works with; the workflow instance is loaded only once an event needs it. */
    private final class Run {

        private final String orgKey;
        private final ResolvedWorkflow workflow;
        private final UUID workflowInstanceId;
        private final Map<String, Object> context;
        private final FlowState state;
        private WorkflowInstance instance;
        private boolean instanceLoaded;

        private Run(String orgKey, ResolvedWorkflow workflow, UUID workflowInstanceId, Map<String, Object> context,
                    FlowState state) {
            this.orgKey = orgKey;
            this.workflow = workflow;
            this.workflowInstanceId = workflowInstanceId;
            this.context = context == null ? Map.of() : context;
            this.state = state;
        }

        private WorkflowInstance instance() {
            if (!instanceLoaded) {
                instance = workflowInstanceRepository.findByOrgKeyAndId(orgKey, workflowInstanceId).orElse(null);
                instanceLoaded = true;
            }
            return instance;
        }
    }
}
