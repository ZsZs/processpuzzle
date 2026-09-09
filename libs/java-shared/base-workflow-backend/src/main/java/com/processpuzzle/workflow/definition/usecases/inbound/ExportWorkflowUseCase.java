package com.processpuzzle.workflow.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.adapters.inbound.WorkflowYamlMapper;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ArtifactYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkflowYamlDocument;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolYamlEntry;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowStartCondition;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports one workflow as a self-contained SPEM YAML document: the workflow entry plus the
 * definitions it needs — every role, artifact, tool and task it references — in the same shape
 * {@link ImportWorkflowsUseCase} consumes. Feeding the result straight back to
 * {@code /workflows/import}, in this organization or another, reproduces the workflow; nothing in the
 * document carries an {@code orgKey}.
 *
 * <p>"References" is read transitively, not literally. A workflow names its roles, artifacts, tools
 * and tasks, but its tasks in turn name artifacts as inputs and outputs, tools in their steps, and
 * the roles able to perform them — leave those out and the exported file describes a workflow whose
 * tasks point at nothing. The four definition sections are therefore unions, each keeping the order
 * the workflow states with the task-derived additions appended.
 */
@Component
@Transactional(readOnly = true, rollbackFor = Exception.class)
public class ExportWorkflowUseCase {

    private final WorkflowRepository repository;
    private final RoleDefinitionRepository roleRepository;
    private final ArtifactDefinitionRepository artifactRepository;
    private final ToolDefinitionRepository toolRepository;
    private final TaskDefinitionRepository taskRepository;
    private final WorkflowYamlMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    public ExportWorkflowUseCase(WorkflowRepository repository,
                                          RoleDefinitionRepository roleRepository,
                                          ArtifactDefinitionRepository artifactRepository,
                                          ToolDefinitionRepository toolRepository,
                                          TaskDefinitionRepository taskRepository,
                                          WorkflowYamlMapper mapper) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.artifactRepository = artifactRepository;
        this.toolRepository = toolRepository;
        this.taskRepository = taskRepository;
        this.mapper = mapper;
    }

    public byte[] execute(String orgKey, String workflowId) throws IOException {
        Workflow workflow = repository.findByOrgKeyAndId(orgKey, workflowId)
                .orElseThrow(() -> new NotFoundException("No workflow with id '%s'".formatted(workflowId)));

        List<TaskDefinition> tasks = loadTasks(orgKey, workflow);
        List<RoleYamlEntry> roles = load(orgKey, roleIds(workflow, tasks),
                roleRepository::findByOrgKeyAndIdIn, RoleDefinition::getId).stream()
                .map(mapper::toRoleYaml).toList();
        List<ArtifactYamlEntry> artifacts = load(orgKey, artifactIds(workflow, tasks),
                artifactRepository::findByOrgKeyAndIdIn, ArtifactDefinition::getId).stream()
                .map(mapper::toArtifactYaml).toList();
        List<ToolYamlEntry> tools = loadTools(orgKey, toolIds(workflow, tasks));
        List<TaskYamlEntry> taskEntries = tasks.stream().map(mapper::toTaskYaml).toList();

        WorkflowYamlDocument document = new WorkflowYamlDocument(
                roles, artifacts, tools, taskEntries, List.of(mapper.toWorkflowYaml(workflow)));
        return yamlMapper.writeValueAsBytes(document);
    }

    // ---------------------------------------------------------------- reference collection

    private Set<String> roleIds(Workflow workflow, List<TaskDefinition> tasks) {
        Set<String> ids = new LinkedHashSet<>(workflow.roleDefinitionIds());
        tasks.forEach(task -> ids.addAll(safeList(task.getPerformedByRoles())));
        addStartConditionRoles(ids, workflow);
        return ids;
    }

    /**
     * A ROLE_DEFINITION start condition names roles nothing else in the workflow has to: it says who
     * may launch it, not who performs anything. Omitting them would export a workflow that cannot be
     * started.
     */
    private void addStartConditionRoles(Set<String> ids, Workflow workflow) {
        WorkflowStartCondition condition = workflow.getStartCondition();
        if (condition != null) {
            ids.addAll(safeList(condition.getAuthorizedRoles()));
        }
    }

    private Set<String> artifactIds(Workflow workflow, List<TaskDefinition> tasks) {
        Set<String> ids = new LinkedHashSet<>(workflow.artifactDefinitionIds());
        for (TaskDefinition task : tasks) {
            ids.addAll(safeList(task.getInputs()));
            ids.addAll(safeList(task.getOutputs()));
        }
        WorkflowStartCondition condition = workflow.getStartCondition();
        if (condition != null) {
            safeList(condition.getRequiredArtifacts())
                    .forEach(required -> ids.add(required.getArtifactDefinitionId()));
        }
        return ids;
    }

    private Set<String> toolIds(Workflow workflow, List<TaskDefinition> tasks) {
        Set<String> ids = new LinkedHashSet<>(workflow.toolDefinitionIds());
        for (TaskDefinition task : tasks) {
            for (StepDefinition step : safeList(task.getSteps())) {
                if (step.getToolDefinitionId() != null) {
                    ids.add(step.getToolDefinitionId());
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- loading

    private List<TaskDefinition> loadTasks(String orgKey, Workflow workflow) {
        Set<String> ids = new LinkedHashSet<>(workflow.taskDefinitionIds());
        return load(orgKey, ids, taskRepository::findByOrgKeyAndIdIn, TaskDefinition::getId);
    }

    /**
     * Reads the named definitions in one query and returns them in the order the ids were collected.
     * A dangling id is skipped rather than raising: the export describes what the workflow is, and a
     * reference the validator would have refused should not make the definition unreadable.
     */
    private <D> List<D> load(String orgKey, Set<String> ids,
                             BiFunction<String, List<String>, List<D>> finder,
                             Function<D, String> idOf) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, D> byId = finder.apply(orgKey, List.copyOf(ids)).stream()
                .collect(Collectors.toMap(idOf, Function.identity()));
        List<D> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            Optional.ofNullable(byId.get(id)).ifPresent(ordered::add);
        }
        return ordered;
    }

    /** Tools have no {@code findByOrgKeyAndIdIn}; the reference count per workflow is small. */
    private List<ToolYamlEntry> loadTools(String orgKey, Set<String> ids) {
        List<ToolYamlEntry> entries = new ArrayList<>(ids.size());
        for (String id : ids) {
            toolRepository.findByOrgKeyAndId(orgKey, id).map(mapper::toToolYaml).ifPresent(entries::add);
        }
        return entries;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
