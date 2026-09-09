package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;

import java.util.List;
import java.util.Optional;

/**
 * A {@link Workflow} with its references followed: the roles, artifacts and tasks it
 * names, fetched from the catalog and paired back up with the workflow's own assignments.
 *
 * <p>This is what the execution layer works against. It exists because the definition layer's
 * split into catalog aggregates is a storage and authoring concern, not a runtime one: an engine
 * advancing an instance needs "the third task of this workflow and who performs it" as one thing,
 * and would otherwise repeat the same join at four call sites.
 *
 * <p>{@link #tasks()} is in assignment order, which is the order the workflow author put them in and
 * the order {@code TaskActivationService} treats as sequential when a task is not
 * {@link TaskUse#isParallel()}.
 */
public record ResolvedWorkflow(
        Workflow definition,
        List<RoleDefinition> roles,
        List<ArtifactDefinition> artifacts,
        List<ResolvedTask> tasks) {

    /** One task of the workflow: the shared definition, plus its place in this workflow. */
    public record ResolvedTask(TaskUse assignment, TaskDefinition definition) {

        public String id() {
            return definition.getId();
        }

        public String performedBy() {
            return assignment.getPerformedBy();
        }

        public List<String> dependsOn() {
            return assignment.getDependsOn() == null ? List.of() : assignment.getDependsOn();
        }

        public JoinType joinType() {
            return assignment.getJoinType() == null ? JoinType.ALL : assignment.getJoinType();
        }

        public boolean parallel() {
            return assignment.isParallel();
        }
    }

    public String id() {
        return definition.getId();
    }

    public Optional<ResolvedTask> findTask(String taskDefinitionId) {
        return tasks.stream().filter(task -> task.id().equals(taskDefinitionId)).findFirst();
    }

    public Optional<RoleDefinition> findRole(String roleId) {
        return roles.stream().filter(role -> role.getId().equals(roleId)).findFirst();
    }
}
