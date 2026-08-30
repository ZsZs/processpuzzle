package com.processpuzzle.workflow.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.workflow.definition.adapters.inbound.WorkflowYamlMapper;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ArtifactYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskUseYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkflowYamlDocument;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkflowYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StartConditionYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StepYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolOperationYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolYamlEntry;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.AuthType;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.domain.WorkflowValidator;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskStepType;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All-or-nothing bulk import of an organization's definition catalog and the workflows composed out
 * of it, mirroring {@code ImportRules} in base-rule-backend: structural validation runs over the
 * whole file first, and nothing is persisted unless every entry passes.
 *
 * <p>Two orderings are load-bearing. The definition sections (roles, artifacts, tools, tasks) are
 * written before the workflows that reference them, so {@link WorkflowValidator} — which resolves
 * every reference through the repositories — sees the same-file catalog and not just what the
 * organization already had. And each section is an <em>upsert</em>, following base-rule rather than
 * base-entity: a create-only importer would make editing an already-seeded definition a dead end.
 *
 * <p>References to base-rule rule ids and base-state state machine ids are recorded as-is and
 * validated lazily at instance start time, not here — see the description of
 * {@code importWorkflows} in base-workflow-api.yaml.
 */
@Component
public class ImportWorkflowsUseCase {

    private static final int MAX_EXTENDS_CHAIN_DEPTH = 100;
    private static final String WORKFLOW_PREFIX = "Workflow '";
    private static final String TASK_PREFIX = "Task '";

    private final WorkflowRepository repository;
    private final RoleDefinitionRepository roleRepository;
    private final ArtifactDefinitionRepository artifactRepository;
    private final ToolDefinitionRepository toolRepository;
    private final TaskDefinitionRepository taskRepository;
    private final WorkflowValidator validator;
    private final WorkflowYamlMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportWorkflowsUseCase(WorkflowRepository repository,
                                            RoleDefinitionRepository roleRepository,
                                            ArtifactDefinitionRepository artifactRepository,
                                            ToolDefinitionRepository toolRepository,
                                            TaskDefinitionRepository taskRepository,
                                            WorkflowValidator validator,
                                            WorkflowYamlMapper mapper) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.artifactRepository = artifactRepository;
        this.toolRepository = toolRepository;
        this.taskRepository = taskRepository;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        WorkflowYamlDocument document = yamlMapper.readValue(input, WorkflowYamlDocument.class);
        List<String> errors = new ArrayList<>();

        Map<String, RoleYamlEntry> roles = index(document.roleDefinitions(), RoleYamlEntry::id, "role", errors);
        Map<String, ArtifactYamlEntry> artifacts =
                index(document.artifactDefinitions(), ArtifactYamlEntry::id, "artifact", errors);
        Map<String, ToolYamlEntry> tools = index(document.toolDefinitions(), ToolYamlEntry::id, "tool", errors);
        Map<String, TaskYamlEntry> tasks = index(document.taskDefinitions(), TaskYamlEntry::id, "task", errors);
        Map<String, WorkflowYamlEntry> workflows = index(document.workflows(), WorkflowYamlEntry::id, "workflow", errors);

        validateArtifacts(artifacts.values(), errors);
        validateTools(tools.values(), errors);
        validateTasks(tasks.values(), errors);
        validateWorkflows(orgKey, workflows, errors);

        if (!errors.isEmpty()) {
            return ImportOutcome.rejected(errors);
        }

        Tally tally = new Tally();
        // Definitions first: the workflow validator resolves references through the repositories.
        upsertRoles(orgKey, roles.values(), tally);
        upsertArtifacts(orgKey, artifacts.values(), tally);
        upsertTools(orgKey, tools.values(), tally);
        upsertTasks(orgKey, tasks.values(), tally);
        upsertWorkflows(orgKey, workflows.values(), tally);

        return new ImportOutcome(tally.created, tally.updated, errors);
    }

    // ---------------------------------------------------------------- indexing

    private <T> Map<String, T> index(List<T> entries, Function<T, String> idOf, String kind, List<String> errors) {
        Map<String, T> byId = new LinkedHashMap<>();
        for (T entry : safeList(entries)) {
            String id = idOf.apply(entry);
            if (id == null || id.isBlank()) {
                errors.add("A " + kind + " entry is missing 'id' and was skipped.");
            } else if (byId.put(id, entry) != null) {
                errors.add("Duplicate " + kind + " id within the import file: '" + id + "'.");
            }
        }
        return byId;
    }

    // ---------------------------------------------------------------- catalog validation

    private void validateArtifacts(Iterable<ArtifactYamlEntry> artifacts, List<String> errors) {
        for (ArtifactYamlEntry artifact : artifacts) {
            if (!WorkflowYamlMapper.isEnumName(ArtifactType.class, artifact.artifactType())) {
                errors.add("Artifact '" + artifact.id() + "' has unknown artifactType '" + artifact.artifactType() + "'.");
            }
        }
    }

    private void validateTools(Iterable<ToolYamlEntry> tools, List<String> errors) {
        for (ToolYamlEntry tool : tools) {
            if (tool.auth() != null && tool.auth().type() != null
                    && !WorkflowYamlMapper.isEnumName(AuthType.class, tool.auth().type())) {
                errors.add("Tool '" + tool.id() + "' has unknown auth type '" + tool.auth().type() + "'.");
            }
            for (ToolOperationYaml operation : safeList(tool.operations())) {
                if (!WorkflowYamlMapper.isEnumName(HttpMethod.class, operation.method())) {
                    errors.add("Tool '" + tool.id() + "', operation '" + operation.id()
                            + "' has unknown method '" + operation.method() + "'.");
                }
            }
        }
    }

    private void validateTasks(Iterable<TaskYamlEntry> tasks, List<String> errors) {
        for (TaskYamlEntry task : tasks) {
            if (safeList(task.performedByRoles()).isEmpty()) {
                errors.add(TASK_PREFIX + task.id() + "' lists no performedByRoles.");
            }
            validateSteps(task, errors);
        }
    }

    private void validateSteps(TaskYamlEntry task, List<String> errors) {
        Set<String> stepIds = new HashSet<>();
        for (StepYamlEntry step : safeList(task.steps())) {
            if (step.id() == null || step.id().isBlank()) {
                errors.add(TASK_PREFIX + task.id() + "' has a step missing 'id'.");
            } else if (!stepIds.add(step.id())) {
                errors.add(TASK_PREFIX + task.id() + "' has duplicate step id '" + step.id() + "'.");
            }
            if (step.stepType() != null && !WorkflowYamlMapper.isEnumName(TaskStepType.class, step.stepType())) {
                errors.add(TASK_PREFIX + task.id() + "', step '" + step.id()
                        + "' has unknown stepType '" + step.stepType() + "'.");
            }
        }
    }

    // ---------------------------------------------------------------- workflow validation

    private void validateWorkflows(String orgKey, Map<String, WorkflowYamlEntry> workflows, List<String> errors) {
        Map<String, String> extendsLinks = buildExtendsLinks(orgKey, workflows);
        for (WorkflowYamlEntry workflow : workflows.values()) {
            validateExtendsLink(workflow, extendsLinks, errors);
            validateTaskUses(workflow, errors);
            validateStartCondition(workflow, errors);
        }
    }

    private Map<String, String> buildExtendsLinks(String orgKey, Map<String, WorkflowYamlEntry> byId) {
        // Only this organization's existing workflows can satisfy an 'extends' reference.
        Map<String, String> extendsLinks = new HashMap<>();
        for (Workflow existing : repository.findByOrgKey(orgKey)) {
            extendsLinks.put(existing.getId(), existing.getExtendsWorkflowId());
        }
        for (WorkflowYamlEntry entry : byId.values()) {
            extendsLinks.put(entry.id(), entry.extendsWorkflowId());
        }
        return extendsLinks;
    }

    private void validateExtendsLink(WorkflowYamlEntry entry, Map<String, String> extendsLinks, List<String> errors) {
        String parentId = entry.extendsWorkflowId();
        if (parentId == null) {
            return;
        }
        if (!extendsLinks.containsKey(parentId)) {
            errors.add(WORKFLOW_PREFIX + entry.id() + "' extends unknown workflow '" + parentId + "'.");
            return;
        }
        if (createsCycle(entry.id(), extendsLinks)) {
            errors.add(WORKFLOW_PREFIX + entry.id() + "' is part of an extends cycle.");
        }
    }

    /**
     * Only what is decidable from the file itself. Whether the referenced roles, artifacts, tools
     * and tasks exist, and whether a {@code performedBy} is one the task offers, is cross-aggregate
     * and belongs to {@link WorkflowValidator}, which runs inside the same transaction.
     */
    private void validateTaskUses(WorkflowYamlEntry entry, List<String> errors) {
        Set<String> used = new HashSet<>();
        for (TaskUseYaml use : safeList(entry.tasks())) {
            String taskId = use.taskDefinitionId();
            if (taskId == null || taskId.isBlank()) {
                errors.add(WORKFLOW_PREFIX + entry.id() + "' has a task use missing 'taskDefinitionId'.");
            } else if (!used.add(taskId)) {
                errors.add(WORKFLOW_PREFIX + entry.id() + "' uses task '" + taskId + "' more than once.");
            }
            if (use.joinType() != null && !WorkflowYamlMapper.isEnumName(JoinType.class, use.joinType())) {
                errors.add(WORKFLOW_PREFIX + entry.id() + "', task '" + taskId
                        + "' has unknown joinType '" + use.joinType() + "'.");
            }
        }
        for (TaskUseYaml use : safeList(entry.tasks())) {
            validateDependsOn(entry, use, used, errors);
        }
    }

    private void validateDependsOn(WorkflowYamlEntry entry, TaskUseYaml use, Set<String> used, List<String> errors) {
        for (String dependsOnId : safeList(use.dependsOn())) {
            if (dependsOnId.equals(use.taskDefinitionId())) {
                errors.add(WORKFLOW_PREFIX + entry.id() + "', task '" + use.taskDefinitionId() + "' dependsOn itself.");
            } else if (!used.contains(dependsOnId)) {
                errors.add(WORKFLOW_PREFIX + entry.id() + "', task '" + use.taskDefinitionId()
                        + "' dependsOn task '" + dependsOnId + "', which the workflow does not use.");
            }
        }
    }

    /**
     * Only the {@code startType} enum, which is decidable from the file. Whether the required
     * artifacts and authorized roles exist is cross-aggregate and belongs to
     * {@link WorkflowValidator}.
     */
    private void validateStartCondition(WorkflowYamlEntry entry, List<String> errors) {
        StartConditionYaml condition = entry.startCondition();
        if (condition == null) {
            return;
        }
        if (!WorkflowYamlMapper.isEnumName(WorkflowStartConditionType.class, condition.startType())) {
            errors.add(WORKFLOW_PREFIX + entry.id() + "' has an unknown startCondition startType '"
                    + condition.startType() + "'.");
        }
    }

    private boolean createsCycle(String startId, Map<String, String> extendsLinks) {
        Set<String> visited = new HashSet<>();
        String cursor = extendsLinks.get(startId);
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(startId) || !visited.add(cursor) || ++depth > MAX_EXTENDS_CHAIN_DEPTH) {
                return true;
            }
            cursor = extendsLinks.get(cursor);
        }
        return false;
    }

    // ---------------------------------------------------------------- persistence

    private void upsertRoles(String orgKey, Iterable<RoleYamlEntry> entries, Tally tally) {
        for (RoleYamlEntry entry : entries) {
            Optional<RoleDefinition> existing = roleRepository.findByOrgKeyAndId(orgKey, entry.id());
            RoleDefinition role = existing.orElseGet(() -> mapper.toRoleDomain(orgKey, entry));
            existing.ifPresent(present -> mapper.applyRole(present, entry));
            tally.count(existing.isPresent());
            roleRepository.save(role);
        }
    }

    private void upsertArtifacts(String orgKey, Iterable<ArtifactYamlEntry> entries, Tally tally) {
        for (ArtifactYamlEntry entry : entries) {
            Optional<ArtifactDefinition> existing = artifactRepository.findByOrgKeyAndId(orgKey, entry.id());
            ArtifactDefinition artifact = existing.orElseGet(() -> mapper.toArtifactDomain(orgKey, entry));
            existing.ifPresent(present -> mapper.applyArtifact(present, entry));
            tally.count(existing.isPresent());
            artifactRepository.save(artifact);
        }
    }

    private void upsertTools(String orgKey, Iterable<ToolYamlEntry> entries, Tally tally) {
        for (ToolYamlEntry entry : entries) {
            Optional<ToolDefinition> existing = toolRepository.findByOrgKeyAndId(orgKey, entry.id());
            ToolDefinition tool = existing.orElseGet(() -> mapper.toToolDomain(orgKey, entry));
            existing.ifPresent(present -> mapper.applyTool(present, entry));
            tally.count(existing.isPresent());
            toolRepository.save(tool);
        }
    }

    private void upsertTasks(String orgKey, Iterable<TaskYamlEntry> entries, Tally tally) {
        for (TaskYamlEntry entry : entries) {
            Optional<TaskDefinition> existing = taskRepository.findByOrgKeyAndId(orgKey, entry.id());
            TaskDefinition task = existing.orElseGet(() -> mapper.toTaskDomain(orgKey, entry));
            existing.ifPresent(present -> mapper.applyTask(present, entry));
            tally.count(existing.isPresent());
            taskRepository.save(task);
        }
    }

    private void upsertWorkflows(String orgKey, Iterable<WorkflowYamlEntry> entries, Tally tally) {
        for (WorkflowYamlEntry entry : entries) {
            Optional<Workflow> existing = repository.findByOrgKeyAndId(orgKey, entry.id());
            Workflow workflow = existing.orElseGet(() -> mapper.toWorkflowDomain(orgKey, entry));
            existing.ifPresent(present -> mapper.applyWorkflow(present, entry));
            tally.count(existing.isPresent());
            validator.validate(workflow);
            repository.save(workflow);
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** Mutable counter pair, so the five upsert loops can report one combined outcome. */
    private static final class Tally {
        private int created;
        private int updated;

        private void count(boolean wasPresent) {
            if (wasPresent) {
                updated++;
            } else {
                created++;
            }
        }
    }
}
