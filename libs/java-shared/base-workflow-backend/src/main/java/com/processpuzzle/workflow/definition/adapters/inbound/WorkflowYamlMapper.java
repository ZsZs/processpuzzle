package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.adapters.inbound.dto.ArtifactUseYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ArtifactYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RequiredStartArtifactYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleUseYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.RoleYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StartConditionYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.StepYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskUseYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.TaskYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolAuthYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolOperationYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolUseYaml;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.ToolYamlEntry;
import com.processpuzzle.workflow.definition.adapters.inbound.dto.WorkflowYamlEntry;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.ArtifactUse;
import com.processpuzzle.workflow.definition.domain.AuthType;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Translates between the SPEM YAML document shape and the definition aggregates, in both
 * directions, for {@code ImportWorkflowsUseCase} and {@code ExportWorkflowUseCase}.
 *
 * <p>Kept apart from {@link WorkflowDefinitionMapper} because the two speak different dialects: the
 * REST mapper works with the generated OpenAPI models, this one with hand-written records whose
 * enum-valued fields are plain strings, matched case-insensitively so a hand-edited seed file may
 * say {@code entity} for {@code ENTITY}.
 *
 * <p>The {@code apply*} methods write onto an existing aggregate rather than replacing it, which is
 * what makes the import an upsert that preserves a definition's identity, audit stamps and
 * optimistic-lock version across re-imports of an edited seed file.
 */
@Component
public class WorkflowYamlMapper {

    // ---------------------------------------------------------------- YAML -> domain

    public RoleDefinition toRoleDomain(String orgKey, RoleYamlEntry entry) {
        RoleDefinition role = RoleDefinition.builder().orgKey(orgKey).id(entry.id()).build();
        applyRole(role, entry);
        return role;
    }

    public void applyRole(RoleDefinition role, RoleYamlEntry entry) {
        role.setName(entry.name());
        role.setDescription(entry.description());
        role.setResponsibleFor(List.copyOf(safeList(entry.responsibleFor())));
        role.setEntityRoleId(entry.entityRoleId());
    }

    public ArtifactDefinition toArtifactDomain(String orgKey, ArtifactYamlEntry entry) {
        ArtifactDefinition artifact = ArtifactDefinition.builder().orgKey(orgKey).id(entry.id()).build();
        applyArtifact(artifact, entry);
        return artifact;
    }

    public void applyArtifact(ArtifactDefinition artifact, ArtifactYamlEntry entry) {
        artifact.setName(entry.name());
        artifact.setDescription(entry.description());
        artifact.setArtifactType(toEnum(ArtifactType.class, entry.artifactType()));
        artifact.setArtifactTypeId(entry.artifactTypeId());
        artifact.setStateMachineId(entry.stateMachineId());
    }

    public ToolDefinition toToolDomain(String orgKey, ToolYamlEntry entry) {
        ToolDefinition tool = ToolDefinition.builder().orgKey(orgKey).id(entry.id()).build();
        applyTool(tool, entry);
        return tool;
    }

    public void applyTool(ToolDefinition tool, ToolYamlEntry entry) {
        tool.setName(entry.name());
        tool.setDescription(entry.description());
        tool.setBaseUrl(entry.baseUrl());
        tool.setAuth(toAuthDomain(entry.auth()));
        tool.setOperations(safeList(entry.operations()).stream().map(this::toOperationDomain).toList());
    }

    public TaskDefinition toTaskDomain(String orgKey, TaskYamlEntry entry) {
        TaskDefinition task = TaskDefinition.builder().orgKey(orgKey).id(entry.id()).build();
        applyTask(task, entry);
        return task;
    }

    public void applyTask(TaskDefinition task, TaskYamlEntry entry) {
        task.setName(entry.name());
        task.setDescription(entry.description());
        task.setPerformedByRoles(List.copyOf(safeList(entry.performedByRoles())));
        task.setInputs(List.copyOf(safeList(entry.inputs())));
        task.setOutputs(List.copyOf(safeList(entry.outputs())));
        task.setPreconditionRuleId(entry.preconditionRuleId());
        task.setPostconditionRuleId(entry.postconditionRuleId());
        task.setSteps(safeList(entry.steps()).stream().map(this::toStepDomain).toList());
    }

    public Workflow toWorkflowDomain(String orgKey, WorkflowYamlEntry entry) {
        Workflow workflow = Workflow.builder().orgKey(orgKey).id(entry.id()).build();
        applyWorkflow(workflow, entry);
        return workflow;
    }

    public void applyWorkflow(Workflow workflow, WorkflowYamlEntry entry) {
        workflow.replaceContent(
                entry.name(),
                entry.description(),
                entry.extendsWorkflowId(),
                toStartConditionDomain(entry.startCondition()),
                safeList(entry.roles()).stream().map(this::toRoleUseDomain).toList(),
                safeList(entry.artifacts()).stream().map(this::toArtifactUseDomain).toList(),
                safeList(entry.tools()).stream().map(this::toToolUseDomain).toList(),
                safeList(entry.tasks()).stream().map(this::toTaskUseDomain).toList());
    }

    private RoleUse toRoleUseDomain(RoleUseYaml entry) {
        return RoleUse.builder().roleDefinitionId(entry.roleDefinitionId()).build();
    }

    private ArtifactUse toArtifactUseDomain(ArtifactUseYaml entry) {
        return ArtifactUse.builder().artifactDefinitionId(entry.artifactDefinitionId()).build();
    }

    private ToolUse toToolUseDomain(ToolUseYaml entry) {
        return ToolUse.builder().toolDefinitionId(entry.toolDefinitionId()).build();
    }

    private TaskUse toTaskUseDomain(TaskUseYaml entry) {
        JoinType joinType = toEnum(JoinType.class, entry.joinType());
        return TaskUse.builder()
                .taskDefinitionId(entry.taskDefinitionId())
                .performedBy(entry.performedBy())
                .dependsOn(List.copyOf(safeList(entry.dependsOn())))
                .joinType(joinType == null ? JoinType.ALL : joinType)
                .parallel(Boolean.TRUE.equals(entry.parallel()))
                .override(Boolean.TRUE.equals(entry.override()))
                .build();
    }

    private WorkflowStartCondition toStartConditionDomain(StartConditionYaml entry) {
        if (entry == null) {
            return null;
        }
        return WorkflowStartCondition.builder()
                .startType(toEnum(WorkflowStartConditionType.class, entry.startType()))
                .requiredArtifacts(safeList(entry.requiredArtifacts()).stream().map(this::toRequiredArtifactDomain).toList())
                .eventType(entry.eventType())
                .payloadMapping(entry.payloadMapping())
                .authorizedRoles(entry.authorizedRoles() == null ? null : List.copyOf(entry.authorizedRoles()))
                .milestoneRef(entry.milestoneRef())
                .preconditionExpression(entry.preconditionExpression())
                .build();
    }

    private RequiredStartArtifact toRequiredArtifactDomain(RequiredStartArtifactYaml entry) {
        return RequiredStartArtifact.builder()
                .artifactDefinitionId(entry.artifactDefinitionId())
                .state(entry.state())
                .build();
    }

    private StepDefinition toStepDomain(StepYamlEntry entry) {
        TaskStepType stepType = toEnum(TaskStepType.class, entry.stepType());
        return StepDefinition.builder()
                .id(entry.id())
                .name(entry.name())
                .description(entry.description())
                .stepType(stepType == null ? TaskStepType.USER_STEP : stepType)
                .toolDefinitionId(entry.toolDefinitionId())
                .toolOperation(entry.toolOperation())
                .inputMapping(entry.inputMapping())
                .outputMapping(entry.outputMapping())
                .build();
    }

    private ToolAuthConfig toAuthDomain(ToolAuthYaml entry) {
        if (entry == null) {
            return ToolAuthConfig.builder().type(AuthType.NONE).build();
        }
        AuthType type = toEnum(AuthType.class, entry.type());
        return ToolAuthConfig.builder()
                .type(type == null ? AuthType.NONE : type)
                .secretRef(entry.secretRef())
                .build();
    }

    private ToolOperation toOperationDomain(ToolOperationYaml entry) {
        return ToolOperation.builder()
                .id(entry.id())
                .method(toEnum(HttpMethod.class, entry.method()))
                .path(entry.path())
                .description(entry.description())
                .payloadTemplate(entry.payloadTemplate())
                .expectedStatusCodes(safeList(entry.expectedStatusCodes()))
                .build();
    }

    // ---------------------------------------------------------------- domain -> YAML

    public RoleYamlEntry toRoleYaml(RoleDefinition role) {
        return new RoleYamlEntry(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getResponsibleFor(),
                role.getEntityRoleId());
    }

    public ArtifactYamlEntry toArtifactYaml(ArtifactDefinition artifact) {
        return new ArtifactYamlEntry(
                artifact.getId(),
                artifact.getName(),
                artifact.getDescription(),
                nameOf(artifact.getArtifactType()),
                artifact.getArtifactTypeId(),
                artifact.getStateMachineId());
    }

    public ToolYamlEntry toToolYaml(ToolDefinition tool) {
        ToolAuthConfig auth = tool.getAuth();
        return new ToolYamlEntry(
                tool.getId(),
                tool.getName(),
                tool.getDescription(),
                tool.getBaseUrl(),
                auth == null ? null : new ToolAuthYaml(nameOf(auth.getType()), auth.getSecretRef()),
                safeList(tool.getOperations()).stream().map(this::toOperationYaml).toList());
    }

    public TaskYamlEntry toTaskYaml(TaskDefinition task) {
        return new TaskYamlEntry(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPerformedByRoles(),
                task.getInputs(),
                task.getOutputs(),
                task.getPreconditionRuleId(),
                task.getPostconditionRuleId(),
                safeList(task.getSteps()).stream().map(this::toStepYaml).toList());
    }

    public WorkflowYamlEntry toWorkflowYaml(Workflow workflow) {
        return new WorkflowYamlEntry(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getExtendsWorkflowId(),
                toStartConditionYaml(workflow.getStartCondition()),
                safeList(workflow.getRoles()).stream()
                        .map(use -> new RoleUseYaml(use.getRoleDefinitionId())).toList(),
                safeList(workflow.getArtifacts()).stream()
                        .map(use -> new ArtifactUseYaml(use.getArtifactDefinitionId())).toList(),
                safeList(workflow.getTools()).stream()
                        .map(use -> new ToolUseYaml(use.getToolDefinitionId())).toList(),
                safeList(workflow.getTasks()).stream().map(this::toTaskUseYaml).toList());
    }

    private TaskUseYaml toTaskUseYaml(TaskUse use) {
        // Boxed booleans, left null when false: the export writes only what was actually decided.
        return new TaskUseYaml(
                use.getTaskDefinitionId(),
                use.getPerformedBy(),
                use.getDependsOn(),
                nameOf(use.getJoinType()),
                use.isParallel() ? Boolean.TRUE : null,
                use.isOverride() ? Boolean.TRUE : null);
    }

    private StartConditionYaml toStartConditionYaml(WorkflowStartCondition condition) {
        if (condition == null) {
            return null;
        }
        return new StartConditionYaml(
                nameOf(condition.getStartType()),
                safeList(condition.getRequiredArtifacts()).stream()
                        .map(artifact -> new RequiredStartArtifactYaml(artifact.getArtifactDefinitionId(), artifact.getState()))
                        .toList(),
                condition.getEventType(),
                condition.getPayloadMapping(),
                condition.getAuthorizedRoles(),
                condition.getMilestoneRef(),
                condition.getPreconditionExpression());
    }

    private StepYamlEntry toStepYaml(StepDefinition step) {
        return new StepYamlEntry(
                step.getId(),
                step.getName(),
                step.getDescription(),
                nameOf(step.getStepType()),
                step.getToolDefinitionId(),
                step.getToolOperation(),
                step.getInputMapping(),
                step.getOutputMapping());
    }

    private ToolOperationYaml toOperationYaml(ToolOperation operation) {
        return new ToolOperationYaml(
                operation.getId(),
                nameOf(operation.getMethod()),
                operation.getPath(),
                operation.getDescription(),
                operation.getPayloadTemplate(),
                operation.getExpectedStatusCodes());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Case-insensitive enum lookup. Returns {@code null} for a null or unknown name rather than
     * throwing: the import validates every enum-valued field up front, so by the time a value
     * reaches here it is either known or the whole file has already been rejected.
     */
    public static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownName) {
            return null;
        }
    }

    /** True when {@code value} names a constant of {@code type}; the import's validation pass. */
    public static <E extends Enum<E>> boolean isEnumName(Class<E> type, String value) {
        return toEnum(type, value) != null;
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
