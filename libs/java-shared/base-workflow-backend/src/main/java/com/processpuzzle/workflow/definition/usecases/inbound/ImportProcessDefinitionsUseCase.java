package com.processpuzzle.workflow.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ProcessYamlDocument;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ProcessYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StepYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskIOReferenceYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkProductYamlEntry;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import com.processpuzzle.workflow.definition.domain.ReferenceType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskIOReference;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import com.processpuzzle.workflow.definition.domain.WorkProductType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All-or-nothing bulk import of process definitions from a SPEM YAML file, mirroring
 * {@code ImportRules} in base-rule-backend: structural + extends-cycle validation runs over the
 * whole file first; nothing is persisted unless every entry passes.
 *
 * <p>References to base-rule rule ids and base-state state machine ids are recorded as-is and
 * validated lazily at instance start time, not here — see the description of
 * {@code importProcessDefinitions} in base-workflow-api.yaml.
 */
@Component
public class ImportProcessDefinitionsUseCase {

    private static final int MAX_EXTENDS_CHAIN_DEPTH = 100;
    private static final String PROCESS_PREFIX = "Process '";

    private final ProcessDefinitionRepository repository;
    private final ProcessDefinitionValidator validator;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportProcessDefinitionsUseCase(ProcessDefinitionRepository repository, ProcessDefinitionValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        ProcessYamlDocument document = yamlMapper.readValue(input, ProcessYamlDocument.class);
        List<ProcessYamlEntry> entries = document.processes() == null ? List.of() : document.processes();

        List<String> errors = new ArrayList<>();
        Map<String, ProcessYamlEntry> byId = indexEntries(entries, errors);

        Map<String, String> extendsLinks = buildExtendsLinks(orgKey, byId);
        validateExtendsLinks(byId, extendsLinks, errors);

        for (ProcessYamlEntry entry : byId.values()) {
            validateStructure(entry, errors);
        }

        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        return persistEntries(orgKey, byId.values(), errors);
    }

    private Map<String, ProcessYamlEntry> indexEntries(List<ProcessYamlEntry> entries, List<String> errors) {
        Map<String, ProcessYamlEntry> byId = new LinkedHashMap<>();
        for (ProcessYamlEntry entry : entries) {
            if (entry.id() == null || entry.id().isBlank()) {
                errors.add("A process entry is missing 'id' and was skipped.");
                continue;
            }
            if (byId.put(entry.id(), entry) != null) {
                errors.add("Duplicate process id within the import file: '" + entry.id() + "'.");
            }
        }
        return byId;
    }

    private Map<String, String> buildExtendsLinks(String orgKey, Map<String, ProcessYamlEntry> byId) {
        // Only this organization's existing processes can satisfy an 'extends' reference.
        Map<String, String> extendsLinks = new HashMap<>();
        for (ProcessDefinition existing : repository.findByOrgKey(orgKey)) {
            extendsLinks.put(existing.getId(), existing.getExtendsProcessId());
        }
        for (ProcessYamlEntry entry : byId.values()) {
            extendsLinks.put(entry.id(), entry.extendsProcessId());
        }
        return extendsLinks;
    }

    private void validateExtendsLinks(Map<String, ProcessYamlEntry> byId, Map<String, String> extendsLinks, List<String> errors) {
        for (ProcessYamlEntry entry : byId.values()) {
            validateExtendsLink(entry, extendsLinks, errors);
        }
    }

    private void validateExtendsLink(ProcessYamlEntry entry, Map<String, String> extendsLinks, List<String> errors) {
        String parentId = entry.extendsProcessId();
        if (parentId == null) {
            return;
        }
        if (!extendsLinks.containsKey(parentId)) {
            errors.add(PROCESS_PREFIX + entry.id() + "' extends unknown process '" + parentId + "'.");
            return;
        }
        if (createsCycle(entry.id(), extendsLinks)) {
            errors.add(PROCESS_PREFIX + entry.id() + "' is part of an extends cycle.");
        }
    }

    private ImportOutcome persistEntries(String orgKey, Iterable<ProcessYamlEntry> entries, List<String> errors) {
        int created = 0;
        int updated = 0;
        for (ProcessYamlEntry entry : entries) {
            Optional<ProcessDefinition> existingOpt = repository.findByOrgKeyAndId(orgKey, entry.id());
            ProcessDefinition process;
            if (existingOpt.isPresent()) {
                process = existingOpt.get();
                applyEntry(process, entry);
                updated++;
            } else {
                process = toNewProcess(orgKey, entry);
                created++;
            }
            validator.validate(process);
            repository.save(process);
        }
        return new ImportOutcome(created, updated, errors);
    }

    private void validateStructure(ProcessYamlEntry entry, List<String> errors) {
        Set<String> roleIds = collectAndValidateRoleIds(entry, errors);
        Set<String> taskIds = collectAndValidateTaskIds(entry, errors);
        validateTaskReferences(entry, roleIds, taskIds, errors);
    }

    private Set<String> collectAndValidateRoleIds(ProcessYamlEntry entry, List<String> errors) {
        Set<String> roleIds = new HashSet<>();
        for (RoleYamlEntry role : safeList(entry.roles())) {
            if (role.id() == null || role.id().isBlank()) {
                errors.add(PROCESS_PREFIX + entry.id() + "' has a role missing 'id'.");
            } else if (!roleIds.add(role.id())) {
                errors.add(PROCESS_PREFIX + entry.id() + "' has duplicate role id '" + role.id() + "'.");
            }
        }
        return roleIds;
    }

    private Set<String> collectAndValidateTaskIds(ProcessYamlEntry entry, List<String> errors) {
        Set<String> taskIds = new HashSet<>();
        for (TaskYamlEntry task : safeList(entry.tasks())) {
            if (task.id() == null || task.id().isBlank()) {
                errors.add(PROCESS_PREFIX + entry.id() + "' has a task missing 'id'.");
            } else if (!taskIds.add(task.id())) {
                errors.add(PROCESS_PREFIX + entry.id() + "' has duplicate task id '" + task.id() + "'.");
            }
        }
        return taskIds;
    }

    private void validateTaskReferences(ProcessYamlEntry entry, Set<String> roleIds, Set<String> taskIds, List<String> errors) {
        for (TaskYamlEntry task : safeList(entry.tasks())) {
            if (task.performedBy() == null || !roleIds.contains(task.performedBy())) {
                errors.add(PROCESS_PREFIX + entry.id() + "', task '" + task.id()
                        + "' is performedBy unknown role '" + task.performedBy() + "'.");
            }
            for (String dependsOnId : safeList(task.dependsOn())) {
                if (!taskIds.contains(dependsOnId)) {
                    errors.add(PROCESS_PREFIX + entry.id() + "', task '" + task.id()
                            + "' dependsOn unknown task '" + dependsOnId + "'.");
                }
            }
        }
    }

    private ProcessDefinition toNewProcess(String orgKey, ProcessYamlEntry entry) {
        ProcessDefinition process = ProcessDefinition.builder()
                .orgKey(orgKey)
                .id(entry.id())
                .build();
        applyEntry(process, entry);
        return process;
    }

    private void applyEntry(ProcessDefinition process, ProcessYamlEntry entry) {
        List<RoleDefinition> roles = safeList(entry.roles()).stream().map(this::toRole).toList();
        List<WorkProductDefinition> workProducts = safeList(entry.workProducts()).stream().map(this::toWorkProduct).toList();
        List<TaskDefinition> tasks = safeList(entry.tasks()).stream().map(this::toTask).toList();

        process.replaceContent(
                entry.name(),
                entry.description(),
                entry.extendsProcessId(),
                safeList(entry.tools()),
                roles,
                workProducts,
                tasks);
    }

    private RoleDefinition toRole(RoleYamlEntry entry) {
        return RoleDefinition.builder()
                .id(entry.id())
                .name(entry.name())
                .description(entry.description())
                .entityRoleId(entry.entityRoleId())
                .build();
    }

    private WorkProductDefinition toWorkProduct(WorkProductYamlEntry entry) {
        return WorkProductDefinition.builder()
                .id(entry.id())
                .name(entry.name())
                .description(entry.description())
                .type(WorkProductType.valueOf(entry.type().toUpperCase(Locale.ROOT)))
                .entityTypeId(entry.entityTypeId())
                .stateMachineId(entry.stateMachineId())
                .build();
    }

    private TaskDefinition toTask(TaskYamlEntry entry) {
        return TaskDefinition.builder()
                .id(entry.id())
                .name(entry.name())
                .description(entry.description())
                .performedBy(entry.performedBy())
                .inputs(safeList(entry.inputs()).stream().map(this::toReference).toList())
                .outputs(safeList(entry.outputs()).stream().map(this::toReference).toList())
                .preconditionRuleId(entry.preconditionRuleId())
                .postconditionRuleId(entry.postconditionRuleId())
                .steps(safeList(entry.steps()).stream().map(this::toStep).toList())
                .dependsOn(safeList(entry.dependsOn()))
                .parallel(Boolean.TRUE.equals(entry.parallel()))
                .override(Boolean.TRUE.equals(entry.override()))
                .build();
    }

    private TaskIOReference toReference(TaskIOReferenceYaml entry) {
        return TaskIOReference.builder()
                .type(ReferenceType.valueOf(entry.type().toUpperCase(Locale.ROOT)))
                .refId(entry.refId())
                .label(entry.label())
                .build();
    }

    private StepDefinition toStep(StepYamlEntry entry) {
        return StepDefinition.builder()
                .id(entry.id())
                .name(entry.name())
                .description(entry.description())
                .toolId(entry.toolId())
                .toolOperation(entry.toolOperation())
                .inputMapping(entry.inputMapping())
                .outputMapping(entry.outputMapping())
                .build();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
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
}
