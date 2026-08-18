package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A unit of work within a {@link ProcessDefinition}, performed by a {@link RoleDefinition}.
 * {@code id} is unique only within the owning process. Not addressable independently: the tasks
 * sub-resource endpoints always resolve through the owning process aggregate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = "technicalId")
@ToString(exclude = "process")
@Entity
@Table(
    name = "workflow_task_definition",
    uniqueConstraints = @UniqueConstraint(columnNames = {"process_technical_id", "id"}))
public class TaskDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "technical_id")
    private UUID technicalId;

    /** Task code, unique within the owning process definition. */
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Role definition id (of the owning process) that performs this task. */
    @Column(nullable = false)
    private String performedBy;

    /** Resources this task reads. See {@link TaskIOReference}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<TaskIOReference> inputs = new ArrayList<>();

    /** Resources this task produces or modifies. Same reference model as inputs. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<TaskIOReference> outputs = new ArrayList<>();

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

    /**
     * Task definition ids (within the same process) that must be COMPLETED before this task can
     * become ACTIVE. Empty means the task is eligible from process start.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /**
     * When true, this task can run concurrently with its siblings that share the same dependsOn.
     * When false (default), tasks within the same dependency level run sequentially in
     * definition order — enforced by {@code TaskActivationService} in the execution layer.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean parallel = false;

    /**
     * When this ProcessDefinition extends another, marks this task as replacing an
     * identically-named task from the parent rather than adding to it.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean override = false;

    @ManyToOne
    @JoinColumn(name = "process_technical_id")
    private ProcessDefinition process;
}
