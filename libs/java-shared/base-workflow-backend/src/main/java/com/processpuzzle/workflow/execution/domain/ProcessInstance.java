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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A running (or finished) execution of a {@code Workflow}. Unlike the definition layer,
 * this is <em>not</em> a single aggregate root owning {@link TaskInstance}/{@link
 * ArtifactInstance} as cascaded child collections: tasks within a process instance are
 * completed concurrently and independently by different users (that's the whole point of
 * {@code TaskUse.parallel}), so forcing every task completion through this entity's
 * {@code @Version} would serialize unrelated task updates against each other. Each of the three
 * runtime entities has its own repository and its own optimistic lock; they're linked by
 * {@code processInstanceId} foreign keys, not JPA {@code @OneToMany} ownership.
 *
 * <p>Splitting the tables is only half of what that claim needs, and for a while it was the only
 * half present: the context used to be one mutable map here that every task completion rewrote, so
 * every completion went through this entity's {@code @Version} anyway and two concurrent
 * completions still raced. {@link #initialContext} is now written once, at start, and each task's
 * own contribution lives on its {@link TaskInstance} — see {@link ProcessContext}. This row is
 * therefore written only when the <em>instance</em> changes state (started, completed, cancelled),
 * which no two tasks do at once.
 *
 * <p>{@code processDefinitionId}/{@code processDefinitionName} are denormalized copies taken at
 * start time — a process instance keeps running under the definition version it started with even
 * if the definition is edited afterward (definitions may not even be deletable while instances are
 * active, per {@code DeleteProcessDefinitionUseCase}, but they remain editable).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "workflow_process_instance")
public class ProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orgKey;

    @Column(nullable = false)
    private String processDefinitionId;

    @Column(nullable = false)
    private String processDefinitionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessInstanceStatus status;

    private String entityId;

    /**
     * The context this instance was started with, and only that: task output is recorded on the task
     * that produced it and folded back in by {@link ProcessContext#assemble}. Written once, which is
     * what keeps concurrent completions of {@code parallel} tasks off this row's optimistic lock.
     *
     * <p>The column is still named {@code context} — renaming it under {@code ddl-auto: update} would
     * orphan the existing one rather than migrate it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> initialContext = new HashMap<>();

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @jakarta.persistence.Version
    private Long version;
}
