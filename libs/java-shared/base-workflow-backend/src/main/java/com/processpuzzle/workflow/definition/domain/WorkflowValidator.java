package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * this module owns neither registry. The same goes for a start condition's {@code milestoneRef} and
 * PPCL expressions; its {@code requiredArtifacts} and {@code authorizedRoles}, however, name this
 * organization's own catalog and so are checked.
 */
@Component
@RequiredArgsConstructor
public class WorkflowValidator {

    private final RoleDefinitionRepository roleRepository;
    private final ArtifactDefinitionRepository artifactRepository;
    private final ToolDefinitionRepository toolRepository;
    private final TaskDefinitionRepository taskRepository;

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

        validateTaskUses(workflow, orgKey, roleIds, taskIds);
        validateStartCondition(workflow, orgKey);
    }

    private void validateTaskUses(Workflow workflow, String orgKey, List<String> roleIds, List<String> taskIds) {
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
            validateDependsOn(use, taskIds);
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

    private List<String> allArtifactsOf(TaskDefinition task) {
        List<String> inputs = task.getInputs() == null ? List.of() : task.getInputs();
        List<String> outputs = task.getOutputs() == null ? List.of() : task.getOutputs();
        return java.util.stream.Stream.concat(inputs.stream(), outputs.stream()).distinct().toList();
    }

    private void validateDependsOn(TaskUse use, List<String> usedTaskIds) {
        List<String> dependsOn = use.getDependsOn() == null ? List.of() : use.getDependsOn();
        for (String dependsOnId : dependsOn) {
            if (dependsOnId.equals(use.getTaskDefinitionId())) {
                throw new ValidationException("Task '%s' cannot depend on itself".formatted(use.getTaskDefinitionId()));
            }
            if (!usedTaskIds.contains(dependsOnId)) {
                throw new ValidationException("Task '%s' dependsOn '%s', which this workflow does not use"
                        .formatted(use.getTaskDefinitionId(), dependsOnId));
            }
        }
    }

    private void validateStartCondition(Workflow workflow, String orgKey) {
        WorkflowStartCondition condition = workflow.getStartCondition();
        if (condition == null) {
            return;
        }
        if (condition.getStartType() == null) {
            throw new ValidationException("Start condition has no startType");
        }

        List<RequiredStartArtifact> required =
                condition.getRequiredArtifacts() == null ? List.of() : condition.getRequiredArtifacts();
        Set<String> declaredArtifacts = new HashSet<>(workflow.artifactDefinitionIds());
        for (RequiredStartArtifact artifact : required) {
            String artifactId = artifact.getArtifactDefinitionId();
            requireExists(artifactRepository.existsByOrgKeyAndId(orgKey, artifactId), "artifact", artifactId);
            if (!declaredArtifacts.contains(artifactId)) {
                throw new ValidationException(
                        "Start condition requires artifact '%s', which the workflow does not declare".formatted(artifactId));
            }
        }

        List<String> authorizedRoles =
                condition.getAuthorizedRoles() == null ? List.of() : condition.getAuthorizedRoles();
        for (String roleId : authorizedRoles) {
            requireExists(roleRepository.existsByOrgKeyAndId(orgKey, roleId), "role", roleId);
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
