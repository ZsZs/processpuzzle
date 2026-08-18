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
 * A running (or finished) execution of a {@code ProcessDefinition}. Unlike the definition layer,
 * this is <em>not</em> a single aggregate root owning {@link TaskInstance}/{@link
 * WorkProductInstance} as cascaded child collections: tasks within a process instance are
 * completed concurrently and independently by different users (that's the whole point of
 * {@code TaskDefinition.parallel}), so forcing every task completion through this entity's
 * {@code @Version} would serialize unrelated task updates against each other. Each of the three
 * runtime entities has its own repository and its own optimistic lock; they're linked by
 * {@code processInstanceId} foreign keys, not JPA {@code @OneToMany} ownership.
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @jakarta.persistence.Version
    private Long version;
}
