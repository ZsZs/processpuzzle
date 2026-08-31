package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
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
 * The reusable half of a task: what it is, which artifacts it reads and writes, which rules guard
 * it, and which roles are <em>able</em> to perform it. A catalog entity like
 * {@link RoleDefinition} — the same task takes part in many workflows.
 *
 * <p>The per-workflow half lives in {@link TaskUse} instead, and has to: which single role performs
 * the task, what must finish before it, and whether it may run beside its siblings are all
 * properties of one <em>workflow</em>, not of the task. {@code dependsOn} names siblings of one
 * workflow and {@code override} belongs to that workflow's {@code extends} chain, so neither can
 * live on something shared.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString
@Entity
@Table(name = "workflow_task_definition")
@IdClass(TaskDefinitionKey.class)
public class TaskDefinition extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    /** Task code, chosen by the author and unique per organization. */
    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Ids of the {@link RoleDefinition}s able to perform this task. A list because the task is
     * shared: each workflow referencing it picks exactly one of these as that workflow's
     * {@link TaskUse#getPerformedBy()}. Naming a role here does not put the task into any
     * workflow.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> performedByRoles = new ArrayList<>();

    /**
     * Ids of the {@link ArtifactDefinition}s this task reads. Ids rather than typed references: an
     * artifact's own {@link ArtifactDefinition#getArtifactType()} already says whether it is an
     * entity, a document or a widget, so anything a task touches is declared as an artifact of the
     * organization.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> inputs = new ArrayList<>();

    /** Ids of the {@link ArtifactDefinition}s this task produces or modifies. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    /**
     * ID of a rule in base-rule. Evaluated when this task is a candidate to become ACTIVE. If it
     * returns false, the task stays PENDING.
     */
    private String preconditionRuleId;

    /**
     * ID of a rule in base-rule. Evaluated when a user calls /complete. If it returns false,
     * completion is rejected and the task stays ACTIVE.
     */
    private String postconditionRuleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<StepDefinition> steps = new ArrayList<>();

    @Version
    private Long version;
}
