package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ReferenceType;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskIOReference;
import com.processpuzzle.workflow.definition.domain.ToolAuthConfig;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.definition.domain.WorkProductDefinition;
import com.processpuzzle.workflow.definition.domain.WorkProductType;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.model.PageOfProcessDefinitionSummary;
import com.processpuzzle.workflow.model.ProcessDefinitionInput;
import com.processpuzzle.workflow.model.ProcessDefinitionSummary;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.StepDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import com.processpuzzle.workflow.model.ToolOperationInput;
import com.processpuzzle.workflow.model.WorkProductDefinitionInput;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Maps between definition-layer domain objects and the generated {@code workflow.model} classes.
 * Mirrors {@code RuleMapper}'s single-class-per-module-side convention.
 *
 * <p><b>Generator-uncertainty note:</b> {@code ProcessDefinitionInput.extends} is a Java reserved
 * word. This code assumes the openapi-generator "spring" generator keeps the semantic getter/setter
 * names ({@code getExtends()}/{@code setExtends(...)}/{@code extends_(...)} builder method) while
 * only renaming the underlying private field — that's the generator's usual behavior for
 * reserved-word properties, but it could not be verified here (no Maven/network access to run
 * {@code mvn generate-sources}). If compilation fails on this one field after a real build,
 * check the generated {@code ProcessDefinitionInput}/{@code ProcessDefinition} source for the
 * actual accessor name and fix it here — everywhere else in this class is ordinary.
 */
@Component
public class WorkflowDefinitionMapper {

    private final ActiveProcessInstanceExistencePort activeInstanceExistencePort;

    public WorkflowDefinitionMapper(ActiveProcessInstanceExistencePort activeInstanceExistencePort) {
        this.activeInstanceExistencePort = activeInstanceExistencePort;
    }

    // -- Process Definition --------------------------------------------

    public ProcessDefinition toDomain(String orgKey, ProcessDefinitionInput input) {
        ProcessDefinition process = ProcessDefinition.builder()
                .orgKey(orgKey)
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .extendsProcessId(input.getExtends()) // see class Javadoc
                .tools(input.getTools() == null ? List.of() : input.getTools())
                .build();

        if (input.getRoles() != null) {
            input.getRoles().forEach(r -> process.addRole(toRoleDomain(r)));
        }
        if (input.getWorkProducts() != null) {
            input.getWorkProducts().forEach(w -> process.addWorkProduct(toWorkProductDomain(w)));
        }
        if (input.getTasks() != null) {
            input.getTasks().forEach(t -> process.addTask(toTaskDomain(t)));
        }
        return process;
    }

    public com.processpuzzle.workflow.model.ProcessDefinition toModel(ProcessDefinition process) {
        com.processpuzzle.workflow.model.ProcessDefinition model = new com.processpuzzle.workflow.model.ProcessDefinition();
        model.setId(process.getId());
        model.setName(process.getName());
        model.setDescription(process.getDescription());
        model.setExtends(process.getExtendsProcessId()); // see class Javadoc
        model.setTools(process.getTools());
        model.setRoles(process.getRoles().stream().map(this::toRoleInputModel).toList());
        model.setWorkProducts(process.getWorkProducts().stream().map(this::toWorkProductModel).toList());
        model.setTasks(process.getTasks().stream().map(this::toTaskInputModel).toList());
        model.setVersion(process.getVersion());
        model.setCreatedAt(toOffsetDateTime(process.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(process.getUpdatedAt()));
        return model;
    }

    public ProcessDefinitionSummary toSummaryModel(ProcessDefinition process) {
        return new ProcessDefinitionSummary()
                .id(process.getId())
                .name(process.getName())
                .description(process.getDescription())
                .version(process.getVersion())
                .activeInstances((int) activeInstanceExistencePort.countActiveInstancesOf(process.getOrgKey(), process.getId()))
                .createdAt(toOffsetDateTime(process.getCreatedAt()))
                .updatedAt(toOffsetDateTime(process.getUpdatedAt()));
    }

    public PageOfProcessDefinitionSummary toModel(Page<ProcessDefinition> page) {
        List<ProcessDefinitionSummary> content = page.getContent().stream().map(this::toSummaryModel).toList();
        return new PageOfProcessDefinitionSummary()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    // -- Role Definition -------------------------------------------------

    public RoleDefinition toRoleDomain(RoleDefinitionInput input) {
        return RoleDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .entityRoleId(input.getEntityRoleId())
                .build();
    }

    public com.processpuzzle.workflow.model.RoleDefinition toRoleModel(RoleDefinition role) {
        return new com.processpuzzle.workflow.model.RoleDefinition()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .entityRoleId(role.getEntityRoleId())
                .processId(role.getProcess() == null ? null : role.getProcess().getId());
    }

    public RoleDefinitionInput toRoleInputModel(RoleDefinition role) {
        return new RoleDefinitionInput()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .entityRoleId(role.getEntityRoleId());
    }

    // -- Work Product Definition ------------------------------------------

    public WorkProductDefinition toWorkProductDomain(WorkProductDefinitionInput input) {
        return WorkProductDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .type(WorkProductType.valueOf(input.getType().getValue()))
                .entityTypeId(input.getEntityTypeId())
                .stateMachineId(input.getStateMachineId())
                .build();
    }

    public com.processpuzzle.workflow.model.WorkProductDefinitionInput toWorkProductModel(WorkProductDefinition workProduct) {
        return new com.processpuzzle.workflow.model.WorkProductDefinitionInput()
                .id(workProduct.getId())
                .name(workProduct.getName())
                .description(workProduct.getDescription())
                .type(com.processpuzzle.workflow.model.WorkProductType.fromValue(workProduct.getType().name()))
                .entityTypeId(workProduct.getEntityTypeId())
                .stateMachineId(workProduct.getStateMachineId());
    }

    // -- Task Definition ---------------------------------------------------

    public TaskDefinition toTaskDomain(TaskDefinitionInput input) {
        return TaskDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .performedBy(input.getPerformedBy())
                .inputs(input.getInputs() == null ? List.of() : input.getInputs().stream().map(this::toReferenceDomain).toList())
                .outputs(input.getOutputs() == null ? List.of() : input.getOutputs().stream().map(this::toReferenceDomain).toList())
                .preconditionRuleId(input.getPreconditionRuleId())
                .postconditionRuleId(input.getPostconditionRuleId())
                .steps(input.getSteps() == null ? List.of() : input.getSteps().stream().map(this::toStepDomain).toList())
                .dependsOn(input.getDependsOn() == null ? List.of() : input.getDependsOn())
                .parallel(Boolean.TRUE.equals(input.getParallel()))
                .override(Boolean.TRUE.equals(input.getOverride()))
                .build();
    }

    public com.processpuzzle.workflow.model.TaskDefinition toTaskModel(TaskDefinition task) {
        return new com.processpuzzle.workflow.model.TaskDefinition()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .performedBy(task.getPerformedBy())
                .inputs(task.getInputs().stream().map(this::toReferenceModel).toList())
                .outputs(task.getOutputs().stream().map(this::toReferenceModel).toList())
                .preconditionRuleId(task.getPreconditionRuleId())
                .postconditionRuleId(task.getPostconditionRuleId())
                .steps(task.getSteps().stream().map(this::toStepModel).toList())
                .dependsOn(task.getDependsOn())
                .parallel(task.isParallel())
                .override(task.isOverride())
                .processId(task.getProcess() == null ? null : task.getProcess().getId());
    }

    public TaskDefinitionInput toTaskInputModel(TaskDefinition task) {
        return new TaskDefinitionInput()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .performedBy(task.getPerformedBy())
                .inputs(task.getInputs().stream().map(this::toReferenceModel).toList())
                .outputs(task.getOutputs().stream().map(this::toReferenceModel).toList())
                .preconditionRuleId(task.getPreconditionRuleId())
                .postconditionRuleId(task.getPostconditionRuleId())
                .steps(task.getSteps().stream().map(this::toStepModel).toList())
                .dependsOn(task.getDependsOn())
                .parallel(task.isParallel())
                .override(task.isOverride());
    }

    private TaskIOReference toReferenceDomain(com.processpuzzle.workflow.model.TaskIOReference input) {
        return TaskIOReference.builder()
                .type(ReferenceType.valueOf(input.getType().getValue()))
                .refId(input.getRefId())
                .label(input.getLabel())
                .build();
    }

    private com.processpuzzle.workflow.model.TaskIOReference toReferenceModel(TaskIOReference reference) {
        return new com.processpuzzle.workflow.model.TaskIOReference()
                .type(com.processpuzzle.workflow.model.ReferenceType.fromValue(reference.getType().name()))
                .refId(reference.getRefId())
                .label(reference.getLabel());
    }

    private StepDefinition toStepDomain(StepDefinitionInput input) {
        return StepDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .toolId(input.getToolId())
                .toolOperation(input.getToolOperation())
                .inputMapping(input.getInputMapping())
                .outputMapping(input.getOutputMapping())
                .build();
    }

    private com.processpuzzle.workflow.model.StepDefinitionInput toStepModel(StepDefinition step) {
        return new com.processpuzzle.workflow.model.StepDefinitionInput()
                .id(step.getId())
                .name(step.getName())
                .description(step.getDescription())
                .toolId(step.getToolId())
                .toolOperation(step.getToolOperation())
                .inputMapping(step.getInputMapping())
                .outputMapping(step.getOutputMapping());
    }

    // -- Tool Definition ----------------------------------------------------

    public ToolDefinition toToolDomain(ToolDefinitionInput input) {
        ToolAuthConfig auth = input.getAuth() == null
                ? ToolAuthConfig.builder().build()
                : ToolAuthConfig.builder()
                        .type(com.processpuzzle.workflow.definition.domain.AuthType.valueOf(input.getAuth().getType().getValue()))
                        .secretRef(input.getAuth().getSecretRef())
                        .build();

        return ToolDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .baseUrl(input.getBaseUrl() == null ? null : input.getBaseUrl().toString())
                .auth(auth)
                .operations(input.getOperations() == null ? List.of() : input.getOperations().stream().map(this::toOperationDomain).toList())
                .build();
    }

    public com.processpuzzle.workflow.model.ToolDefinition toToolModel(ToolDefinition tool) {
        var authModel = new com.processpuzzle.workflow.model.ToolAuthConfig()
                .type(com.processpuzzle.workflow.model.AuthType.fromValue(tool.getAuth().getType().name()))
                .secretRef(tool.getAuth().getSecretRef());

        return new com.processpuzzle.workflow.model.ToolDefinition()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .baseUrl(tool.getBaseUrl() == null ? null : URI.create(tool.getBaseUrl()))
                .auth(authModel)
                .operations(tool.getOperations().stream().map(this::toOperationModel).toList())
                .version(tool.getVersion())
                .createdAt(toOffsetDateTime(tool.getCreatedAt()));
    }

    private ToolOperation toOperationDomain(ToolOperationInput input) {
        return ToolOperation.builder()
                .id(input.getId())
                .method(com.processpuzzle.workflow.definition.domain.HttpMethod.valueOf(input.getMethod().getValue()))
                .path(input.getPath())
                .description(input.getDescription())
                .payloadTemplate(input.getPayloadTemplate())
                .expectedStatusCodes(input.getExpectedStatusCodes())
                .build();
    }

    private com.processpuzzle.workflow.model.ToolOperationInput toOperationModel(ToolOperation operation) {
        return new com.processpuzzle.workflow.model.ToolOperationInput()
                .id(operation.getId())
                .method(com.processpuzzle.workflow.model.HttpMethod.fromValue(operation.getMethod().name()))
                .path(operation.getPath())
                .description(operation.getDescription())
                .payloadTemplate(operation.getPayloadTemplate())
                .expectedStatusCodes(operation.getExpectedStatusCodes());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
