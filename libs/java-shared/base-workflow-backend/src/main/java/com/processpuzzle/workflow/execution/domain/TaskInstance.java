package com.processpuzzle.workflow.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime state of one {@code TaskDefinition} within a {@link ProcessInstance}. {@code orgKey}
 * and {@code processInstanceId} are denormalized onto every row (rather than relying solely on a
 * JPA relationship) so the task endpoints -- always addressed as
 * {@code /instances/{instanceId}/tasks/{taskId}} -- can look a row up directly without loading the
 * owning process instance first.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "workflow_task_instance")
public class TaskInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orgKey;

    @Column(nullable = false)
    private UUID processInstanceId;

    /** ID of the TaskDefinition this instance was created from, unique within the process. */
    @Column(nullable = false)
    private String taskDefinitionId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskInstanceStatus status;

    /** User id (from base-entity) currently assigned, set by AssignTaskUseCase. */
    private String assignedTo;

    /** Set when status is BLOCKED: the precondition rule's violation detail. */
    private String blockedReason;

    private Instant activatedAt;
    private Instant completedAt;
    private Instant skippedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<StepResult> stepResults = new ArrayList<>();

    @jakarta.persistence.Version
    private Long version;
}
