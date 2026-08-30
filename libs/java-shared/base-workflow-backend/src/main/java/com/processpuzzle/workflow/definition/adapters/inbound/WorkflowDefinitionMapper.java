package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.JoinType;
import com.processpuzzle.workflow.definition.domain.RequiredStartArtifact;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskStepType;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.ToolAuthConfig;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.definition.domain.ToolUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowStartCondition;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.model.ArtifactDefinitionInput;
import com.processpuzzle.workflow.model.PageOfWorkflow;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.TaskStepDefinition;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import com.processpuzzle.workflow.model.ToolOperationDefinition;
import com.processpuzzle.workflow.model.WorkflowInput;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Maps between definition-layer domain objects and the generated {@code workflow.model} classes.
 * Mirrors {@code RuleMapper}'s single-class-per-module-side convention.
 *
 * <p>Roles, artifacts, tasks and tools are catalog aggregates of their own, so each has a plain
 * {@code to*Domain} / {@code to*Model} pair, and a workflow maps to <em>uses</em> of them plus its
 * own start condition. Nothing here follows a reference: turning a use's id back into a definition
 * is {@code ResolveProcessDefinitionUseCase}'s job, and doing it in a mapper would make every list
 * response fetch the whole catalog.
 *
 * <p>{@code version} travels <em>into</em> the domain object on the way in, which is what makes the
 * replace use cases' optimistic-lock check reachable at all: each compares the caller's version
 * against the stored one, and while {@code version} lived only on the read schemas that comparison
 * read a field nothing ever populated — so the contract's promise of lost-update protection could not
 * hold. A null version means the caller did not opt in, and the write proceeds unconditionally.
 *
 * <p>Two naming notes. The generated model and the domain class share a simple name for all five
 * aggregates, so the model side is spelled out in full wherever both appear. And
 * {@code WorkflowInput.extends} is a Java reserved word; the generator keeps the semantic
 * {@code getExtends()}/{@code setExtends(...)} accessors and renames only the field.
 */
@Component
public class WorkflowDefinitionMapper {

    private final ActiveProcessInstanceExistencePort activeInstanceExistencePort;

    public WorkflowDefinitionMapper(ActiveProcessInstanceExistencePort activeInstanceExistencePort) {
        this.activeInstanceExistencePort = activeInstanceExistencePort;
    }

    // -- Workflow ------------------------------------------------------

    public Workflow toDomain(String orgKey, WorkflowInput input) {
        return Workflow.builder()
                .orgKey(orgKey)
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .extendsProcessId(input.getExtends()) // see class Javadoc
                .version(input.getVersion()) // null unless the caller opted into the lock check
                .startCondition(toStartConditionDomain(input.getStartCondition()))
                .roles(mapEach(input.getRoles(), this::toRoleUseDomain))
                .artifacts(mapEach(input.getArtifacts(), this::toArtifactUseDomain))
                .tools(mapEach(input.getTools(), this::toToolUseDomain))
                .tasks(mapEach(input.getTasks(), this::toTaskUseDomain))
                .build();
    }

    public com.processpuzzle.workflow.model.Workflow toModel(Workflow workflow) {
        com.processpuzzle.workflow.model.Workflow model = new com.processpuzzle.workflow.model.Workflow();
        model.setId(workflow.getId());
        model.setName(workflow.getName());
        model.setDescription(workflow.getDescription());
        model.setExtends(workflow.getExtendsProcessId()); // see class Javadoc
        model.setStartCondition(toStartConditionModel(workflow.getStartCondition()));
        model.setRoles(mapEach(workflow.getRoles(), this::toRoleUseModel));
        model.setArtifacts(mapEach(workflow.getArtifacts(), this::toArtifactUseModel));
        model.setTools(mapEach(workflow.getTools(), this::toToolUseModel));
        model.setTasks(mapEach(workflow.getTasks(), this::toTaskUseModel));
        model.setVersion(workflow.getVersion());
        model.setCreatedAt(toOffsetDateTime(workflow.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(workflow.getUpdatedAt()));
        return model;
    }

    /**
     * One page of <em>full</em> workflows, uses and start condition included.
     *
     * <p>Not a summary projection, and deliberately so: base-entity's generated form reads the record
     * out of the list its store already loaded rather than re-fetching it by id, so a lighter shape
     * here would render an empty form whose save — a full replacement — destroyed everything the
     * projection had dropped. {@code activeInstances} is the one field the list needs that the
     * aggregate does not carry, so it is computed per row.
     */
    public PageOfWorkflow toModel(Page<Workflow> page) {
        List<com.processpuzzle.workflow.model.Workflow> content = page.getContent().stream()
                .map(this::toListModel)
                .toList();
        return new PageOfWorkflow()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    private com.processpuzzle.workflow.model.Workflow toListModel(Workflow workflow) {
        com.processpuzzle.workflow.model.Workflow model = toModel(workflow);
        model.setActiveInstances((int) activeInstanceExistencePort.countActiveInstancesOf(workflow.getOrgKey(), workflow.getId()));
        return model;
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    // -- Uses ----------------------------------------------------------

    public RoleUse toRoleUseDomain(com.processpuzzle.workflow.model.RoleUse input) {
        return RoleUse.builder().roleDefinitionId(input.getRoleDefinitionId()).build();
    }

    public com.processpuzzle.workflow.model.RoleUse toRoleUseModel(RoleUse use) {
        return new com.processpuzzle.workflow.model.RoleUse().roleDefinitionId(use.getRoleDefinitionId());
    }

    public ArtifactUse toArtifactUseDomain(com.processpuzzle.workflow.model.ArtifactUse input) {
        return ArtifactUse.builder().artifactDefinitionId(input.getArtifactDefinitionId()).build();
    }

    public com.processpuzzle.workflow.model.ArtifactUse toArtifactUseModel(ArtifactUse use) {
        return new com.processpuzzle.workflow.model.ArtifactUse().artifactDefinitionId(use.getArtifactDefinitionId());
    }

    public ToolUse toToolUseDomain(com.processpuzzle.workflow.model.ToolUse input) {
        return ToolUse.builder().toolDefinitionId(input.getToolDefinitionId()).build();
    }

    public com.processpuzzle.workflow.model.ToolUse toToolUseModel(ToolUse use) {
        return new com.processpuzzle.workflow.model.ToolUse().toolDefinitionId(use.getToolDefinitionId());
    }

    public TaskUse toTaskUseDomain(com.processpuzzle.workflow.model.TaskUse input) {
        return TaskUse.builder()
                .taskDefinitionId(input.getTaskDefinitionId())
                .performedBy(input.getPerformedBy())
                .dependsOn(copyOf(input.getDependsOn()))
                .joinType(input.getJoinType() == null ? JoinType.ALL : JoinType.valueOf(input.getJoinType().getValue()))
                .parallel(Boolean.TRUE.equals(input.getParallel()))
                .override(Boolean.TRUE.equals(input.getOverride()))
                .build();
    }

    public com.processpuzzle.workflow.model.TaskUse toTaskUseModel(TaskUse use) {
        return new com.processpuzzle.workflow.model.TaskUse()
                .taskDefinitionId(use.getTaskDefinitionId())
                .performedBy(use.getPerformedBy())
                .dependsOn(copyOf(use.getDependsOn()))
                .joinType(com.processpuzzle.workflow.model.JoinType.fromValue(
                        (use.getJoinType() == null ? JoinType.ALL : use.getJoinType()).name()))
                .parallel(use.isParallel())
                .override(use.isOverride());
    }

    // -- Start condition -----------------------------------------------

    private WorkflowStartCondition toStartConditionDomain(com.processpuzzle.workflow.model.WorkflowStartCondition input) {
        if (input == null) {
            return null;
        }
        return WorkflowStartCondition.builder()
                .startType(input.getStartType() == null
                        ? null
                        : WorkflowStartConditionType.valueOf(input.getStartType().getValue()))
                .requiredArtifacts(mapEach(input.getRequiredArtifacts(), this::toRequiredArtifactDomain))
                .eventType(input.getEventType())
                .payloadMapping(input.getPayloadMapping())
                .authorizedRoles(input.getAuthorizedRoles() == null ? null : copyOf(input.getAuthorizedRoles()))
                .milestoneRef(input.getMilestoneRef())
                .preconditionExpression(input.getPreconditionExpression())
                .build();
    }

    private com.processpuzzle.workflow.model.WorkflowStartCondition toStartConditionModel(WorkflowStartCondition condition) {
        if (condition == null) {
            return null;
        }
        return new com.processpuzzle.workflow.model.WorkflowStartCondition()
                .startType(condition.getStartType() == null
                        ? null
                        : com.processpuzzle.workflow.model.WorkflowStartConditionType.fromValue(condition.getStartType().name()))
                .requiredArtifacts(mapEach(condition.getRequiredArtifacts(), this::toRequiredArtifactModel))
                .eventType(condition.getEventType())
                .payloadMapping(condition.getPayloadMapping())
                .authorizedRoles(condition.getAuthorizedRoles() == null ? null : copyOf(condition.getAuthorizedRoles()))
                .milestoneRef(condition.getMilestoneRef())
                .preconditionExpression(condition.getPreconditionExpression());
    }

    private RequiredStartArtifact toRequiredArtifactDomain(com.processpuzzle.workflow.model.RequiredStartArtifact input) {
        return RequiredStartArtifact.builder()
                .artifactDefinitionId(input.getArtifactDefinitionId())
                .state(input.getState())
                .build();
    }

    private com.processpuzzle.workflow.model.RequiredStartArtifact toRequiredArtifactModel(RequiredStartArtifact artifact) {
        return new com.processpuzzle.workflow.model.RequiredStartArtifact()
                .artifactDefinitionId(artifact.getArtifactDefinitionId())
                .state(artifact.getState());
    }

    // -- Role Definition -----------------------------------------------

    public RoleDefinition toRoleDomain(RoleDefinitionInput input) {
        return RoleDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .responsibleFor(copyOf(input.getResponsibleFor()))
                .entityRoleId(input.getEntityRoleId())
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.workflow.model.RoleDefinition toRoleModel(RoleDefinition role) {
        return new com.processpuzzle.workflow.model.RoleDefinition()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .responsibleFor(copyOf(role.getResponsibleFor()))
                .entityRoleId(role.getEntityRoleId())
                .version(role.getVersion())
                .createdAt(toOffsetDateTime(role.getCreatedAt()))
                .updatedAt(toOffsetDateTime(role.getUpdatedAt()));
    }


    // -- Artifact Definition -------------------------------------------

    public ArtifactDefinition toArtifactDomain(ArtifactDefinitionInput input) {
        return ArtifactDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .artifactType(input.getArtifactType() == null
                        ? null
                        : ArtifactType.valueOf(input.getArtifactType().getValue()))
                .artifactTypeId(input.getArtifactTypeId())
                .stateMachineId(input.getStateMachineId())
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.workflow.model.ArtifactDefinition toArtifactModel(ArtifactDefinition artifact) {
        return new com.processpuzzle.workflow.model.ArtifactDefinition()
                .id(artifact.getId())
                .name(artifact.getName())
                .description(artifact.getDescription())
                .artifactType(toArtifactTypeModel(artifact))
                .artifactTypeId(artifact.getArtifactTypeId())
                .stateMachineId(artifact.getStateMachineId())
                .version(artifact.getVersion())
                .createdAt(toOffsetDateTime(artifact.getCreatedAt()))
                .updatedAt(toOffsetDateTime(artifact.getUpdatedAt()));
    }


    private com.processpuzzle.workflow.model.ArtifactType toArtifactTypeModel(ArtifactDefinition artifact) {
        return artifact.getArtifactType() == null
                ? null
                : com.processpuzzle.workflow.model.ArtifactType.fromValue(artifact.getArtifactType().name());
    }

    // -- Task Definition -----------------------------------------------

    public TaskDefinition toTaskDomain(TaskDefinitionInput input) {
        return TaskDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .performedByRoles(copyOf(input.getPerformedByRoles()))
                .inputs(copyOf(input.getInputs()))
                .outputs(copyOf(input.getOutputs()))
                .preconditionRuleId(input.getPreconditionRuleId())
                .postconditionRuleId(input.getPostconditionRuleId())
                .steps(mapEach(input.getSteps(), this::toStepDomain))
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.workflow.model.TaskDefinition toTaskModel(TaskDefinition task) {
        return new com.processpuzzle.workflow.model.TaskDefinition()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .performedByRoles(copyOf(task.getPerformedByRoles()))
                .inputs(copyOf(task.getInputs()))
                .outputs(copyOf(task.getOutputs()))
                .preconditionRuleId(task.getPreconditionRuleId())
                .postconditionRuleId(task.getPostconditionRuleId())
                .steps(mapEach(task.getSteps(), this::toStepModel))
                .version(task.getVersion())
                .createdAt(toOffsetDateTime(task.getCreatedAt()))
                .updatedAt(toOffsetDateTime(task.getUpdatedAt()));
    }


    private StepDefinition toStepDomain(TaskStepDefinition input) {
        return StepDefinition.builder()
                .id(input.getId())
                .name(input.getName())
                .description(input.getDescription())
                .stepType(input.getStepType() == null
                        ? TaskStepType.USER_STEP
                        : TaskStepType.valueOf(input.getStepType().getValue()))
                .toolDefinitionId(input.getToolDefinitionId())
                .toolOperation(input.getToolOperation())
                .inputMapping(input.getInputMapping())
                .outputMapping(input.getOutputMapping())
                .build();
    }

    private TaskStepDefinition toStepModel(StepDefinition step) {
        return new TaskStepDefinition()
                .id(step.getId())
                .name(step.getName())
                .description(step.getDescription())
                .stepType(com.processpuzzle.workflow.model.TaskStepType.fromValue(
                        (step.getStepType() == null ? TaskStepType.USER_STEP : step.getStepType()).name()))
                .toolDefinitionId(step.getToolDefinitionId())
                .toolOperation(step.getToolOperation())
                .inputMapping(step.getInputMapping())
                .outputMapping(step.getOutputMapping());
    }

    // -- Tool Definition -----------------------------------------------

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
                .operations(mapEach(input.getOperations(), this::toOperationDomain))
                .version(input.getVersion())
                .build();
    }

    public com.processpuzzle.workflow.model.ToolDefinition toToolModel(ToolDefinition tool) {
        return new com.processpuzzle.workflow.model.ToolDefinition()
                .id(tool.getId())
                .name(tool.getName())
                .description(tool.getDescription())
                .baseUrl(tool.getBaseUrl() == null ? null : URI.create(tool.getBaseUrl()))
                .auth(toAuthModel(tool))
                .operations(mapEach(tool.getOperations(), this::toOperationModel))
                .version(tool.getVersion())
                .createdAt(toOffsetDateTime(tool.getCreatedAt()))
                .updatedAt(toOffsetDateTime(tool.getUpdatedAt()));
    }


    private com.processpuzzle.workflow.model.ToolAuthConfig toAuthModel(ToolDefinition tool) {
        ToolAuthConfig auth = tool.getAuth() == null ? ToolAuthConfig.builder().build() : tool.getAuth();
        return new com.processpuzzle.workflow.model.ToolAuthConfig()
                .type(com.processpuzzle.workflow.model.AuthType.fromValue(auth.getType().name()))
                .secretRef(auth.getSecretRef());
    }

    private ToolOperation toOperationDomain(ToolOperationDefinition input) {
        return ToolOperation.builder()
                .id(input.getId())
                .method(com.processpuzzle.workflow.definition.domain.HttpMethod.valueOf(input.getMethod().getValue()))
                .path(input.getPath())
                .description(input.getDescription())
                .payloadTemplate(input.getPayloadTemplate())
                .expectedStatusCodes(input.getExpectedStatusCodes())
                .build();
    }

    private ToolOperationDefinition toOperationModel(ToolOperation operation) {
        return new ToolOperationDefinition()
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

    /** Generated models default their list properties to a mutable empty list; domain builders do not. */
    private List<String> copyOf(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private <S, T> List<T> mapEach(List<S> source, java.util.function.Function<S, T> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }
}
