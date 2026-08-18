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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

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
        return roles.isEmpty() ? null : roles.stream()
                .map(r -> new RoleYamlEntry(r.getId(), r.getName(), r.getDescription(), r.getEntityRoleId()))
                .toList();
    }

    private List<WorkProductYamlEntry> toWorkProductEntries(List<WorkProductDefinition> workProducts) {
        return workProducts.isEmpty() ? null : workProducts.stream()
                .map(w -> new WorkProductYamlEntry(
                        w.getId(), w.getName(), w.getDescription(),
                        w.getType() == null ? null : w.getType().name(),
                        w.getEntityTypeId(), w.getStateMachineId()))
                .toList();
    }

    private List<TaskYamlEntry> toTaskEntries(List<TaskDefinition> tasks) {
        return tasks.isEmpty() ? null : tasks.stream()
                .map(t -> new TaskYamlEntry(
                        t.getId(), t.getName(), t.getDescription(), t.getPerformedBy(),
                        toReferenceEntries(t.getInputs()), toReferenceEntries(t.getOutputs()),
                        t.getPreconditionRuleId(), t.getPostconditionRuleId(),
                        toStepEntries(t.getSteps()),
                        t.getDependsOn().isEmpty() ? null : t.getDependsOn(),
                        t.isParallel() ? Boolean.TRUE : null,
                        t.isOverride() ? Boolean.TRUE : null))
                .toList();
    }

    private List<TaskIOReferenceYaml> toReferenceEntries(List<TaskIOReference> references) {
        return references == null || references.isEmpty() ? null : references.stream()
                .map(r -> new TaskIOReferenceYaml(r.getType() == null ? null : r.getType().name(), r.getRefId(), r.getLabel()))
                .toList();
    }

    private List<StepYamlEntry> toStepEntries(List<StepDefinition> steps) {
        return steps == null || steps.isEmpty() ? null : steps.stream()
                .map(s -> new StepYamlEntry(
                        s.getId(), s.getName(), s.getDescription(), s.getToolId(), s.getToolOperation(),
                        s.getInputMapping(), s.getOutputMapping()))
                .toList();
    }
}
