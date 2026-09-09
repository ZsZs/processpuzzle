package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The aggregate root of the definition layer: a SPEM-inspired workflow composed out of the
 * organization's catalog. {@link RoleDefinition}, {@link ArtifactDefinition},
 * {@link TaskDefinition} and {@link ToolDefinition} are aggregates of their own, shared across
 * workflows, so this one holds <em>uses</em> of them rather than owning documents.
 *
 * <p>A use — {@link RoleUse}, {@link ArtifactUse}, {@link ToolUse}, {@link TaskUse} — names a
 * definition by id and carries whatever is true of it only in this workflow. That is why the four
 * lists hold objects and not bare ids: {@link TaskUse} already needs the extra fields, and the
 * other three can grow them without changing the shape of this aggregate. There is no
 * {@code WorkflowUse} and no {@code WorkflowDefinition}, because a workflow is not reused inside
 * another one — it is {@link #extendsWorkflowId extended}, which is a different relation.
 *
 * <p>Storing the four lists as JSONB keeps the whole workflow in a single row, which is what makes
 * {@link #version} a meaningful optimistic-lock guard over the entire definition, as "Use version
 * to prevent lost updates" in base-workflow-api.yaml promises.
 *
 * <p>Composite key (orgKey, id) mirrors {@code RuleDefinition}'s convention: {@code id} is the
 * author-chosen business identifier (e.g. {@code "order-fulfillment-workflow"}), unique per tenant.
 *
 * <p>The table is still named {@code workflow_definition}: the REST resource is called a
 * workflow ({@code /workflows}, {@code WorkflowsApi}) and renaming the table under
 * {@code ddl-auto: update} would orphan the existing one rather than migrate it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString
@Entity
@Table(name = "workflow_definition")
@IdClass(WorkflowKey.class)
public class Workflow extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @SuppressWarnings("java:S1450")
    private String description;

    /**
     * ID of another workflow (same org) this one extends. Roles, artifacts, tools and task uses are
     * inherited unless overridden by a use of the same task marked {@code override=true} — see
     * {@link TaskUse#isOverride()}. Cycle safety is enforced by {@link WorkflowExtendsValidator},
     * mirroring {@code RuleExtendsValidator} in base-rule-backend.
     */
    @SuppressWarnings("java:S1450")
    private String extendsWorkflowId;

    /**
     * How an instance of this workflow comes into being. Null means it can only be started
     * explicitly through {@code /instances}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private WorkflowStartCondition startCondition;

    /** The {@link RoleDefinition}s (same org) taking part in this workflow. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<RoleUse> roles = new ArrayList<>();

    /** The {@link ArtifactDefinition}s (same org) this workflow produces or consumes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<ArtifactUse> artifacts = new ArrayList<>();

    /** The {@link ToolDefinition}s (same org) usable by this workflow's task steps. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<ToolUse> tools = new ArrayList<>();

    /** The tasks of this workflow, each pinned to the role performing it here. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<TaskUse> tasks = new ArrayList<>();

    @Version
    private Long version;

    public Optional<TaskUse> findTaskUse(String taskDefinitionId) {
        return tasks.stream().filter(use -> use.getTaskDefinitionId().equals(taskDefinitionId)).findFirst();
    }

    /** Ids of the referenced role definitions, in declaration order. */
    public List<String> roleDefinitionIds() {
        return roles.stream().map(RoleUse::getRoleDefinitionId).toList();
    }

    /** Ids of the referenced artifact definitions, in declaration order. */
    public List<String> artifactDefinitionIds() {
        return artifacts.stream().map(ArtifactUse::getArtifactDefinitionId).toList();
    }

    /** Ids of the referenced tool definitions, in declaration order. */
    public List<String> toolDefinitionIds() {
        return tools.stream().map(ToolUse::getToolDefinitionId).toList();
    }

    /** Ids of the used task definitions, in declaration order. */
    public List<String> taskDefinitionIds() {
        return tasks.stream().map(TaskUse::getTaskDefinitionId).toList();
    }

    /**
     * Replaces the whole referenced graph in-place, preserving this aggregate's identity and
     * {@link #version}. Used by the workflow-level PUT — the same "clear and rebuild owned
     * collections" convention {@code ReplaceEntityDefinitionUseCase} uses for
     * {@code BaseEntityAttribute}.
     */
    public void replaceContent(String name, String description, String extendsWorkflowId,
                                WorkflowStartCondition startCondition, List<RoleUse> roles,
                                List<ArtifactUse> artifacts, List<ToolUse> tools, List<TaskUse> tasks) {
        this.name = name;
        this.description = description;
        this.extendsWorkflowId = extendsWorkflowId;
        this.startCondition = startCondition;
        this.roles = new ArrayList<>(roles);
        this.artifacts = new ArrayList<>(artifacts);
        this.tools = new ArrayList<>(tools);
        this.tasks = new ArrayList<>(tasks);
    }
}
