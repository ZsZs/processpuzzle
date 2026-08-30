package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Follows a process definition's references into the catalog and hands back a {@link
 * ResolvedProcess}. The execution layer's entry point into the definition layer.
 *
 * <p>Resolution is strict: a reference that no longer names an existing catalog entry is a {@link
 * NotFoundException} rather than a silently skipped row. It cannot normally happen —
 * {@link com.processpuzzle.workflow.definition.domain.WorkflowValidator} refuses a process
 * naming something absent, and the catalog delete guards refuse to remove something referenced —
 * but if the two ever disagree, an instance that quietly ran with fewer tasks than its definition
 * declares would be far worse than a refusal to start.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResolveProcessDefinitionUseCase {

    private final WorkflowRepository processRepository;
    private final RoleDefinitionRepository roleRepository;
    private final ArtifactDefinitionRepository artifactRepository;
    private final TaskDefinitionRepository taskRepository;

    public ResolvedProcess resolveByOrgKeyAndId(String orgKey, String processId) {
        Workflow definition = processRepository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No workflow with id '%s'".formatted(processId)));
        return resolve(definition);
    }

    public ResolvedProcess resolve(Workflow definition) {
        String orgKey = definition.getOrgKey();

        List<String> roleIds = definition.roleDefinitionIds();
        List<RoleDefinition> roles = inReferenceOrder(
                roleIds,
                byId(roleRepository.findByOrgKeyAndIdIn(orgKey, copyOf(roleIds)), RoleDefinition::getId),
                "role");
        List<String> artifactIds = definition.artifactDefinitionIds();
        List<ArtifactDefinition> artifacts = inReferenceOrder(
                artifactIds,
                byId(artifactRepository.findByOrgKeyAndIdIn(orgKey, copyOf(artifactIds)), ArtifactDefinition::getId),
                "artifact");

        List<String> taskIds = definition.taskDefinitionIds();
        Map<String, TaskDefinition> tasksById =
                byId(taskRepository.findByOrgKeyAndIdIn(orgKey, copyOf(taskIds)), TaskDefinition::getId);

        List<ResolvedProcess.ResolvedTask> tasks = new ArrayList<>();
        for (TaskUse assignment : definition.getTasks()) {
            TaskDefinition task = tasksById.get(assignment.getTaskDefinitionId());
            if (task == null) {
                throw new NotFoundException("Process '%s' assigns unknown task '%s'"
                        .formatted(definition.getId(), assignment.getTaskDefinitionId()));
            }
            tasks.add(new ResolvedProcess.ResolvedTask(assignment, task));
        }

        return new ResolvedProcess(definition, roles, artifacts, tasks);
    }

    private <T> List<T> inReferenceOrder(List<String> referencedIds, Map<String, T> byId, String kind) {
        List<T> resolved = new ArrayList<>();
        for (String id : copyOf(referencedIds)) {
            T entry = byId.get(id);
            if (entry == null) {
                throw new NotFoundException("No %s definition with id '%s'".formatted(kind, id));
            }
            resolved.add(entry);
        }
        return resolved;
    }

    private <T> Map<String, T> byId(List<T> entries, Function<T, String> idOf) {
        return entries.stream().collect(Collectors.toMap(idOf, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }

    /** {@code findByOrgKeyAndIdIn} with an empty list is a valid query; a null list is not. */
    private List<String> copyOf(List<String> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }
}
