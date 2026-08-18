package com.processpuzzle.workflow.definition.usecases.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ProcessYamlDocument;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ProcessYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StepYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskIOReferenceYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkProductYamlEntry;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskIOReference;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports a single process definition as SPEM YAML — one entry in the same {@code processes:}
 * document shape {@link ImportProcessDefinitionsUseCase} consumes, so an exported file can be
 * re-imported (into the same or a different organization; the entry carries no {@code orgKey}).
 */
@Component
@Transactional(readOnly = true)
public class ExportProcessDefinitionUseCase {

    private final ProcessDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportProcessDefinitionUseCase(ProcessDefinitionRepository repository) {
        this.repository = repository;
    }

    public byte[] execute(String orgKey, String processId) throws IOException {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        ProcessYamlEntry entry = new ProcessYamlEntry(
                process.getId(),
                process.getName(),
                process.getDescription(),
                process.getExtendsProcessId(),
                process.getTools().isEmpty() ? null : process.getTools(),
                toRoleEntries(process.getRoles()),
                toWorkProductEntries(process.getWorkProducts()),
                toTaskEntries(process.getTasks()));

        return yamlMapper.writeValueAsBytes(new ProcessYamlDocument(List.of(entry)));
    }

    private List<RoleYamlEntry> toRoleEntries(List<RoleDefinition> roles) {
        if (roles.isEmpty()) {
            return null;
        }
        return roles.stream()
                .map(r -> new RoleYamlEntry(r.getId(), r.getName(), r.getDescription(), r.getEntityRoleId()))
                .toList();
    }

    private List<WorkProductYamlEntry> toWorkProductEntries(List<WorkProductDefinition> workProducts) {
        if (workProducts.isEmpty()) {
            return null;
        }
        return workProducts.stream()
                .map(this::toWorkProductEntry)
                .toList();
    }

    private WorkProductYamlEntry toWorkProductEntry(WorkProductDefinition w) {
        String typeName = w.getType() != null ? w.getType().name() : null;
        return new WorkProductYamlEntry(
                w.getId(), w.getName(), w.getDescription(),
                typeName,
                w.getEntityTypeId(), w.getStateMachineId());
    }

    private List<TaskYamlEntry> toTaskEntries(List<TaskDefinition> tasks) {
        if (tasks.isEmpty()) {
            return null;
        }
        return tasks.stream()
                .map(this::toTaskEntry)
                .toList();
    }

    private TaskYamlEntry toTaskEntry(TaskDefinition t) {
        List<String> dependsOn = t.getDependsOn().isEmpty() ? null : t.getDependsOn();
        Boolean parallel = t.isParallel() ? Boolean.TRUE : null;
        Boolean override = t.isOverride() ? Boolean.TRUE : null;
        return new TaskYamlEntry(
                t.getId(), t.getName(), t.getDescription(), t.getPerformedBy(),
                toReferenceEntries(t.getInputs()), toReferenceEntries(t.getOutputs()),
                t.getPreconditionRuleId(), t.getPostconditionRuleId(),
                toStepEntries(t.getSteps()),
                dependsOn,
                parallel,
                override);
    }

    private List<TaskIOReferenceYaml> toReferenceEntries(List<TaskIOReference> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        return references.stream()
                .map(this::toReferenceEntry)
                .toList();
    }

    private TaskIOReferenceYaml toReferenceEntry(TaskIOReference r) {
        String typeName = r.getType() != null ? r.getType().name() : null;
        return new TaskIOReferenceYaml(typeName, r.getRefId(), r.getLabel());
    }

    private List<StepYamlEntry> toStepEntries(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        return steps.stream()
                .map(s -> new StepYamlEntry(
                        s.getId(), s.getName(), s.getDescription(), s.getToolId(), s.getToolOperation(),
                        s.getInputMapping(), s.getOutputMapping()))
                .toList();
    }
}
