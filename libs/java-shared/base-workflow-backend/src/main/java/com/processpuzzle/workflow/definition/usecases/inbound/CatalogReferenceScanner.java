package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Answers "what still points at this catalog entry?" for the delete guards.
 *
 * <p>An in-memory scan, not a query: the references live in JSONB columns
 * ({@link Workflow#getRoles()}, a task's {@link TaskDefinition#getInputs()}) rather than in foreign
 * keys, so a database-side check would need dialect-specific jsonb containment SQL. That is a fair
 * trade at catalog scale — the same one {@code DeleteToolDefinitionUseCase} already made, and
 * {@code WorkflowExtendsValidator} reads a tenant's workflows whole for the same reason. Revisit if
 * a tenant ever has enough definitions for it to matter.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogReferenceScanner {

    private final WorkflowRepository workflowRepository;
    private final TaskDefinitionRepository taskRepository;
    private final RoleDefinitionRepository roleRepository;

    /** Ids of the workflows whose own role uses or task uses name {@code roleId}. */
    public List<String> workflowsUsingRole(String orgKey, String roleId) {
        return workflowIdsWhere(orgKey, workflow -> workflow.roleDefinitionIds().contains(roleId)
                || workflow.getTasks().stream().map(TaskUse::getPerformedBy).anyMatch(roleId::equals));
    }

    /** Ids of the task definitions offering {@code roleId} among their performedByRoles. */
    public List<String> tasksOfferingRole(String orgKey, String roleId) {
        return taskRepository.findByOrgKey(orgKey).stream()
                .filter(task -> safe(task.getPerformedByRoles()).contains(roleId))
                .map(TaskDefinition::getId)
                .toList();
    }

    public List<String> workflowsUsingArtifact(String orgKey, String artifactId) {
        return workflowIdsWhere(orgKey, workflow -> workflow.artifactDefinitionIds().contains(artifactId));
    }

    /** Ids of the task definitions naming {@code artifactId} among their inputs or outputs. */
    public List<String> tasksReferencingArtifact(String orgKey, String artifactId) {
        return taskRepository.findByOrgKey(orgKey).stream()
                .filter(task -> referencesArtifact(task, artifactId))
                .map(TaskDefinition::getId)
                .toList();
    }

    /**
     * Ids of the role definitions claiming {@code artifactId} in responsibleFor. A third holder of
     * artifact references beside workflows and tasks, and one the delete guard has to know about:
     * ownership is as much a reference as use is.
     */
    public List<String> rolesResponsibleForArtifact(String orgKey, String artifactId) {
        return roleRepository.findByOrgKey(orgKey).stream()
                .filter(role -> safe(role.getResponsibleFor()).contains(artifactId))
                .map(RoleDefinition::getId)
                .toList();
    }

    public List<String> workflowsAssigningTask(String orgKey, String taskId) {
        return workflowIdsWhere(orgKey, workflow -> workflow.findTaskUse(taskId).isPresent());
    }

    public List<String> workflowsUsingTool(String orgKey, String toolId) {
        return workflowIdsWhere(orgKey, workflow -> workflow.toolDefinitionIds().contains(toolId));
    }

    /** Ids of the task definitions whose steps invoke {@code toolId}. */
    public List<String> tasksUsingTool(String orgKey, String toolId) {
        return taskRepository.findByOrgKey(orgKey).stream()
                .filter(task -> safe(task.getSteps()).stream()
                        .map(StepDefinition::getToolDefinitionId)
                        .anyMatch(toolId::equals))
                .map(TaskDefinition::getId)
                .toList();
    }

    private boolean referencesArtifact(TaskDefinition task, String artifactId) {
        return Stream.concat(safe(task.getInputs()).stream(), safe(task.getOutputs()).stream())
                .anyMatch(artifactId::equals);
    }

    private List<String> workflowIdsWhere(String orgKey, Predicate<Workflow> predicate) {
        return workflowRepository.findByOrgKey(orgKey).stream()
                .filter(predicate)
                .map(Workflow::getId)
                .toList();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
