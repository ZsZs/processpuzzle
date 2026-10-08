package com.processpuzzle.workflow.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * When a TIME_BASED_PRECONDITION start event of a workflow starts it next. One row per such start event,
 * kept in step with the definition by {@code StartTimerScheduleReconciler}, and polled by
 * {@code TimerSweep} the way a waiting timer catch is.
 *
 * <p>{@link #specHash} is the timer the row was armed from. A definition saved again with the same timer
 * keeps the row — and so its {@link #dueAt} — rather than re-arming it, which for a cycle would push its
 * next firing out by a whole interval on every save.
 *
 * <p>{@link #version} is what keeps two sweeps — two replicas — from starting the same firing twice: each
 * advances the row before starting, and the second one's advance fails on the lock.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Entity
@Table(name = "workflow_start_timer_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_wf_start_timer",
                columnNames = {"org_key", "workflow_id", "start_event_id"}),
        indexes = @Index(name = "idx_wf_start_timer_due", columnList = "due_at"))
public class StartTimerSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Column(name = "start_event_id", nullable = false)
    private String startEventId;

    /** When the workflow starts next; null once the timer has no firing left. */
    @Column(name = "due_at")
    private Instant dueAt;

    /** Firings left after the one at {@link #dueAt}; null for an unbounded cycle. */
    @Column(name = "remaining_firings")
    private Integer remainingFirings;

    @Column(name = "spec_hash", nullable = false)
    private String specHash;

    @Version
    private Long version;
}
