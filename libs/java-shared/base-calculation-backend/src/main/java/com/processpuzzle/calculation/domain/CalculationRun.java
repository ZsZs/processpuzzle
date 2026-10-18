package com.processpuzzle.calculation.domain;

import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One computation of a calculation, kept with what explains it: the definition version and a snapshot of the source
 * it ran, the parameters, the results and the trace of every decision. The snapshot is what keeps a frozen result
 * explainable after its definition changed or was deleted, without a history of definitions.
 *
 * <p>The subject is the object the run is about — the output's target, else the object a trigger bound. A frozen
 * run freezes its subject: no later run of the calculation recomputes it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(of = {"id", "orgKey", "calculationId", "status", "frozen"})
@Entity
@Table(name = "calculation_run", indexes = {
        @Index(name = "idx_calculation_run_subject", columnList = "org_key, calculation_id, subject_id")})
public class CalculationRun {

    @Id
    private UUID id;

    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Column(name = "calculation_id", nullable = false)
    private String calculationId;

    @Column(name = "definition_version", nullable = false)
    private int definitionVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Channel channel;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "definition_source", nullable = false)
    private String definitionSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", length = 16, nullable = false)
    private RunTrigger triggeredBy;

    @Column(name = "subject_type")
    private String subjectType;

    @Column(name = "subject_id")
    private String subjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> parameters = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private RunStatus status;

    @Column(length = 4000)
    private String error;

    /** The evaluation result's values, rows and groups as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> result = new LinkedHashMap<>();

    /** The trace entries as JSON, in evaluation order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> trace = new ArrayList<>();

    private int written;

    private boolean frozen;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Freezes a completed run; a frozen one stays as it is. */
    public boolean freeze(Instant at) {
        if (status != RunStatus.COMPLETED) {
            throw new IllegalStateException("Run '%s' %s; only a completed run can be frozen".formatted(id, status));
        }
        if (frozen) {
            return false;
        }
        frozen = true;
        frozenAt = at;
        return true;
    }
}
