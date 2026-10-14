package com.processpuzzle.release.domain;

import com.processpuzzle.core.definition.MigrationStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A {@link MigrationStep} of an organization: pending until a release runs it, then part of that release's record —
 * its number, and what the step did — and never changed again. A correction is a step of a later release.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "org_migration_steps")
public class OrgMigrationStep {

    /** What a release did with the step. */
    public enum Outcome {
        APPLIED, SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    /** Where the step stands among the organization's pending steps: the order they run in. */
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;

    @Column(nullable = false, updatable = false, length = 32)
    private String kind;

    @Column(name = "definition_key", nullable = false, updatable = false)
    private String definitionKey;

    @Column(nullable = false, updatable = false, length = 64)
    private String operation;

    @Column(updatable = false)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> parameters = new LinkedHashMap<>();

    /** The release that ran the step; null while it is pending. */
    @Column(name = "release_number")
    private Long releaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Outcome outcome;

    @Column(name = "affected_objects")
    private Long affectedObjects;

    @Column(length = 1024)
    private String reason;

    public OrgMigrationStep(String orgKey, int sequenceNo, MigrationStep step) {
        this.orgKey = orgKey;
        this.sequenceNo = sequenceNo;
        this.kind = step.kind();
        this.definitionKey = step.key();
        this.operation = step.operation();
        this.subject = step.subject();
        this.parameters = new LinkedHashMap<>(step.parameters());
    }

    public MigrationStep toStep() {
        return new MigrationStep(id == null ? null : id.toString(), kind, definitionKey, operation, subject, parameters);
    }

    /** Records that release {@code number} ran the step, and what it did. */
    public void releasedIn(long number, Outcome outcome, Long affectedObjects, String reason) {
        this.releaseNumber = number;
        this.outcome = outcome;
        this.affectedObjects = affectedObjects;
        this.reason = reason;
    }
}
