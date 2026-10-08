package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.definition.domain.EventDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
 * Run-time state of one intermediate {@code EventUse} within a {@link WorkflowInstance} — the event
 * counterpart of {@link TaskInstance}, and denormalized the same way.
 *
 * <p>A THROW goes PENDING → THROWN when the flow reaches it; {@link #occurrenceId} is then its own
 * {@link #id}, which is what a catch elsewhere records to recognise a redelivered copy. A CATCH goes
 * PENDING → WAITING when reached and WAITING → OCCURRED when its event arrives, recording the
 * occurrence's id, its {@link #payload} and what its payload mapping {@link #contextContribution
 * contributed} to the workflow context. Either may be CANCELLED.
 *
 * <p>Its own row and its own {@link #version}, for the reason {@link WorkflowContext} gives: an event
 * occurring writes only this row, so it never contends with a task completed beside it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "workflow_event_instance", indexes = {
        @Index(name = "idx_wf_event_instance_instance", columnList = "org_key, workflow_instance_id"),
        @Index(name = "idx_wf_event_instance_waiting", columnList = "org_key, event_definition_id, status"),
        @Index(name = "idx_wf_event_instance_occurrence", columnList = "org_key, occurrence_id")})
public class EventInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Column(name = "workflow_instance_id", nullable = false)
    private UUID workflowInstanceId;

    /** The {@code EventUse} of the workflow this row runs. */
    @Column(name = "event_use_id", nullable = false)
    private String eventUseId;

    @Column(name = "event_definition_id", nullable = false)
    private String eventDefinitionId;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventInstanceStatus status;

    /**
     * THROW — the value sent. CATCH — the value waited for: the correlation variable's value for a
     * MESSAGE, the instance's entityId otherwise.
     */
    @Column(name = "correlation_value")
    private String correlationValue;

    /** THROW — this row's own id. CATCH — the id of the occurrence delivered to it. */
    @Column(name = "occurrence_id")
    private UUID occurrenceId;

    private Instant waitingSince;

    /** When a catch's event arrived, or when a throw was raised. */
    private Instant occurredAt;

    /** The payload sent (THROW) or received (CATCH). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** CATCH — what the payload mapping added to the workflow context. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> contextContribution;

    @Version
    private Long version;

    public boolean isThrow() {
        return direction == EventDirection.THROW;
    }

    public boolean isCatch() {
        return direction == EventDirection.CATCH;
    }
}
