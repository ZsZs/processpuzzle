package com.processpuzzle.workflow.execution.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assembles a workflow instance's context out of the instance's initial values and each completed
 * task's {@link TaskInstance#getContextContribution() contribution}, rather than out of one
 * accumulated field.
 *
 * <p><b>Why the context is folded instead of accumulated.</b> It used to live in a single mutable
 * map on {@link WorkflowInstance}, rewritten by every task completion. That made
 * {@link WorkflowInstance#getVersion()} a lock on the whole run: two users completing two
 * {@code parallel} tasks concurrently both read the instance at the same version, both merged their
 * output into that one map, and the second commit lost the optimistic-lock race — so the very
 * feature the three-table split was meant to enable did not work concurrently. Deriving the context
 * on read instead means a task completion writes only its own row.
 *
 * <p><b>Ordering.</b> Contributions fold in completion order, so a later task overwrites an earlier
 * one's value for the same key — the same last-writer-wins semantics the accumulated map had.
 * Instances with no {@code completedAt} (still running, skipped, blocked) contribute nothing. Equal
 * timestamps are broken by task definition id so that the result is stable rather than dependent on
 * row order, which matters because two steps of one completion can share an instant.
 *
 * <p>A utility rather than a service: it needs no collaborators, and both callers
 * ({@code CompleteTaskUseCase} and {@code WorkflowExecutionMapper}) already hold the task instances
 * it folds — the mapper because the API returns them alongside the instance, so assembling costs no
 * extra query.
 */
public final class WorkflowContext {

    private static final Comparator<TaskInstance> IN_COMPLETION_ORDER =
            Comparator.comparing(TaskInstance::getCompletedAt).thenComparing(TaskInstance::getTaskDefinitionId);

    private WorkflowContext() {
        // utility
    }

    /**
     * The effective context of {@code instance}: its initial values with every completed task's
     * contribution folded in, in completion order.
     */
    public static Map<String, Object> assemble(WorkflowInstance instance, List<TaskInstance> tasks) {
        Map<String, Object> assembled = new LinkedHashMap<>(safeMap(instance == null ? null : instance.getInitialContext()));
        safeList(tasks).stream()
                .filter(task -> task.getCompletedAt() != null)
                .sorted(IN_COMPLETION_ORDER)
                .forEach(task -> assembled.putAll(safeMap(task.getContextContribution())));
        return assembled;
    }

    /**
     * What a task changed: every entry of {@code after} that {@code before} did not already have with
     * the same value. This is how a contribution is derived rather than declared, which is what lets
     * it capture both the caller's supplied context and whatever the task's tool steps mapped back —
     * two sources that would otherwise have to be threaded through the executor separately.
     *
     * <p>Entries are never removed from a context, so a key present in {@code before} and absent from
     * {@code after} is treated as unchanged rather than as a deletion.
     */
    public static Map<String, Object> contributionOf(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> beforeSafe = safeMap(before);
        Map<String, Object> contribution = new LinkedHashMap<>();
        safeMap(after).forEach((key, value) -> {
            if (!beforeSafe.containsKey(key) || !Objects.equals(beforeSafe.get(key), value)) {
                contribution.put(key, value);
            }
        });
        return contribution;
    }

    private static Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static List<TaskInstance> safeList(List<TaskInstance> tasks) {
        return tasks == null ? List.of() : tasks;
    }
}
