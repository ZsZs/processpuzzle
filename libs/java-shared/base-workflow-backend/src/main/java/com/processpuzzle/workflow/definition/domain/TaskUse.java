package com.processpuzzle.workflow.definition.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One task's place in one workflow: which {@link TaskDefinition} takes part, which of its
 * {@link TaskDefinition#getPerformedByRoles()} performs it here, what has to finish first, and
 * whether it may run beside its siblings.
 *
 * <p>The per-workflow half of a task has to live here rather than on the shared definition: {@link
 * #dependsOn} names siblings of one workflow and {@link #override} belongs to that workflow's
 * {@code extends} chain, so neither can sit on something reused elsewhere.
 *
 * <p>A task appears at most once per workflow. That is what lets {@link #dependsOn} keep naming a
 * plain task definition id, and it makes the role-performs-task pair unique within the workflow by
 * construction rather than by a check.
 *
 * <p>Not a JPA entity: {@link Workflow#getTasks()} stores the list as a single JSONB column, so a
 * no-arg-constructible getter/setter POJO is all Jackson needs. It also keeps the whole workflow in
 * one row, which is what makes {@link Workflow#getVersion()} a lock over the entire definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskUse {

    /** Id of a {@link TaskDefinition} of the same organization. */
    private String taskDefinitionId;

    /**
     * Role definition id. Has to be one of the workflow's {@link Workflow#getRoles()} <em>and</em>
     * one of the referenced task's {@code performedByRoles} — the task says who is able to perform
     * it, the workflow says who does.
     */
    private String performedBy;

    /**
     * Task definition ids, of task uses of this same workflow, that must reach a terminal status
     * before this task can become ACTIVE. Empty means the task is eligible from workflow start.
     */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /** How {@link #dependsOn} is satisfied. Null is read as {@link JoinType#ALL}. */
    @Builder.Default
    private JoinType joinType = JoinType.ALL;

    /**
     * When true, this task can run concurrently with its siblings that share the same
     * {@link #dependsOn}. When false (default), tasks within the same dependency level run
     * sequentially in declaration order — enforced by {@code TaskActivationService}.
     */
    @Builder.Default
    private boolean parallel = false;

    /**
     * When the owning workflow extends another, marks this use as replacing the parent's use of the
     * same task rather than adding to it.
     */
    @Builder.Default
    private boolean override = false;
}
