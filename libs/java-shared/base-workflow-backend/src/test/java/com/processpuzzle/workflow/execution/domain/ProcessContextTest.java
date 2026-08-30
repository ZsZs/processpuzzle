package com.processpuzzle.workflow.execution.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fold that replaced the accumulated context map. Worth testing on its own because it is what
 * makes {@code parallel} task completion possible: the alternative — one mutable map on
 * {@link ProcessInstance} — put every completion through that entity's optimistic lock.
 */
class ProcessContextTest {

    @Test
    void assembleStartsFromTheInstancesInitialContext() {
        ProcessInstance instance = instanceWith(Map.of("orderId", "o-1"));

        assertThat(ProcessContext.assemble(instance, List.of())).containsExactlyEntriesOf(Map.of("orderId", "o-1"));
    }

    @Test
    void assembleFoldsContributionsOverTheInitialContext() {
        ProcessInstance instance = instanceWith(Map.of("orderId", "o-1"));
        List<TaskInstance> tasks = List.of(
                completed("review", 100, Map.of("reviewedBy", "clerk")),
                completed("approve", 200, Map.of("approvedBy", "manager")));

        assertThat(ProcessContext.assemble(instance, tasks))
                .containsEntry("orderId", "o-1")
                .containsEntry("reviewedBy", "clerk")
                .containsEntry("approvedBy", "manager")
                .hasSize(3);
    }

    /** Last writer wins, which is the semantics the accumulated map had — hence "completion order". */
    @Test
    void alaterContributionOverwritesAnEarlierOneAndTheInitialValue() {
        ProcessInstance instance = instanceWith(Map.of("state", "DRAFT"));
        List<TaskInstance> tasks = List.of(
                completed("approve", 300, Map.of("state", "APPROVED")),
                completed("review", 100, Map.of("state", "REVIEWED")));

        // Declared out of order on purpose: the fold sorts, it does not trust row order.
        assertThat(ProcessContext.assemble(instance, tasks)).containsEntry("state", "APPROVED");
    }

    /**
     * Two completions in the same instant are possible — an in-memory H2 test does it routinely — so
     * the tie-break has to be deterministic or the assembled context depends on row order.
     */
    @Test
    void contributionsWithTheSameTimestampFoldInTaskIdOrder() {
        ProcessInstance instance = instanceWith(Map.of());
        List<TaskInstance> ascending = List.of(
                completed("aaa", 100, Map.of("winner", "aaa")),
                completed("zzz", 100, Map.of("winner", "zzz")));
        List<TaskInstance> descending = List.of(
                completed("zzz", 100, Map.of("winner", "zzz")),
                completed("aaa", 100, Map.of("winner", "aaa")));

        assertThat(ProcessContext.assemble(instance, ascending)).containsEntry("winner", "zzz");
        assertThat(ProcessContext.assemble(instance, descending)).containsEntry("winner", "zzz");
    }

    /** Only completed tasks contribute: a task still running has not decided anything yet. */
    @Test
    void assembleIgnoresTasksThatHaveNotCompleted() {
        ProcessInstance instance = instanceWith(Map.of("orderId", "o-1"));
        TaskInstance active = TaskInstance.builder()
                .taskDefinitionId("review")
                .status(TaskInstanceStatus.ACTIVE)
                .contextContribution(new HashMap<>(Map.of("shouldNotAppear", true)))
                .build();
        TaskInstance skipped = TaskInstance.builder()
                .taskDefinitionId("approve")
                .status(TaskInstanceStatus.SKIPPED)
                .skippedAt(Instant.ofEpochMilli(100))
                .build();

        assertThat(ProcessContext.assemble(instance, List.of(active, skipped)))
                .containsExactlyEntriesOf(Map.of("orderId", "o-1"));
    }

    @Test
    void assembleToleratesNullsEverywhere() {
        TaskInstance nullContribution = TaskInstance.builder()
                .taskDefinitionId("review").completedAt(Instant.ofEpochMilli(100)).build();
        nullContribution.setContextContribution(null);
        ProcessInstance nullInitial = ProcessInstance.builder().build();
        nullInitial.setInitialContext(null);

        assertThat(ProcessContext.assemble(null, null)).isEmpty();
        assertThat(ProcessContext.assemble(nullInitial, null)).isEmpty();
        assertThat(ProcessContext.assemble(nullInitial, List.of(nullContribution))).isEmpty();
    }

    /** The assembled map is a fresh copy; mutating it must not reach back into the entities. */
    @Test
    void assembleReturnsACopy() {
        ProcessInstance instance = instanceWith(new HashMap<>(Map.of("orderId", "o-1")));

        ProcessContext.assemble(instance, List.of()).put("injected", true);

        assertThat(instance.getInitialContext()).doesNotContainKey("injected");
    }

    // ---------------------------------------------------------------- contributionOf

    @Test
    void contributionIsTheEntriesAddedOrChanged() {
        Map<String, Object> before = Map.of("orderId", "o-1", "state", "DRAFT");
        Map<String, Object> after = Map.of("orderId", "o-1", "state", "REVIEWED", "reviewedBy", "clerk");

        assertThat(ProcessContext.contributionOf(before, after))
                .containsEntry("state", "REVIEWED")
                .containsEntry("reviewedBy", "clerk")
                .hasSize(2);
    }

    /** An unchanged pass-through contributes nothing, so a task that decides nothing records nothing. */
    @Test
    void contributionIsEmptyWhenNothingChanged() {
        Map<String, Object> unchanged = Map.of("orderId", "o-1");

        assertThat(ProcessContext.contributionOf(unchanged, unchanged)).isEmpty();
        assertThat(ProcessContext.contributionOf(null, null)).isEmpty();
        assertThat(ProcessContext.contributionOf(Map.of("a", 1), null)).isEmpty();
    }

    /**
     * A key set to null is a change, not an absence — {@code containsKey} rather than a null-check is
     * what distinguishes them, and a tool response mapping an absent field yields exactly this.
     */
    @Test
    void settingAKeyToNullCountsAsAContribution() {
        Map<String, Object> before = Map.of("orderId", "o-1");
        Map<String, Object> after = new HashMap<>(before);
        after.put("stockConfirmed", null);

        assertThat(ProcessContext.contributionOf(before, after)).containsExactlyEntriesOf(after.entrySet().stream()
                .filter(entry -> "stockConfirmed".equals(entry.getKey()))
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll));
    }

    /** Nothing removes from a context, so a key that vanished is treated as unchanged, not deleted. */
    @Test
    void aMissingKeyIsNotTreatedAsARemoval() {
        assertThat(ProcessContext.contributionOf(Map.of("orderId", "o-1", "state", "DRAFT"), Map.of("orderId", "o-1")))
                .isEmpty();
    }

    /**
     * The round trip the completion path actually performs: derive a contribution from what the task
     * changed, then fold it back and get the same context the task was working with.
     */
    @Test
    void contributionFoldsBackToTheWorkingContext() {
        ProcessInstance instance = instanceWith(Map.of("orderId", "o-1"));
        Map<String, Object> inherited = ProcessContext.assemble(instance, List.of());

        Map<String, Object> working = new HashMap<>(inherited);
        working.put("reviewedBy", "clerk");
        working.put("orderId", "o-1-corrected");

        TaskInstance completed = completed("review", 100, ProcessContext.contributionOf(inherited, working));

        assertThat(ProcessContext.assemble(instance, List.of(completed))).isEqualTo(working);
    }

    // ---------------------------------------------------------------- fixtures

    private ProcessInstance instanceWith(Map<String, Object> initialContext) {
        return ProcessInstance.builder().initialContext(new HashMap<>(initialContext)).build();
    }

    private TaskInstance completed(String taskDefinitionId, long completedAtMillis, Map<String, Object> contribution) {
        return TaskInstance.builder()
                .taskDefinitionId(taskDefinitionId)
                .status(TaskInstanceStatus.COMPLETED)
                .completedAt(Instant.ofEpochMilli(completedAtMillis))
                .contextContribution(new HashMap<>(contribution))
                .build();
    }
}
