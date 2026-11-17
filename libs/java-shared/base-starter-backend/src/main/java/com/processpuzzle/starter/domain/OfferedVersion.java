package com.processpuzzle.starter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The version of a starter that ProcessPuzzle has rolled out: new sign-ups install it, and the organizations that run
 * an older one are told an upgrade is available. One row per starter; withdrawing it deletes the row, and the
 * organizations that already applied it keep it.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "starter_offered_version")
public class OfferedVersion {

    @Id
    @Column(name = "starter_id", nullable = false, updatable = false)
    private String starterId;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private Instant offeredAt;

    private String offeredBy;

    public OfferedVersion(String starterId) {
        this.starterId = starterId;
    }

    public void offer(String version, Instant offeredAt, String offeredBy) {
        this.version = version;
        this.offeredAt = offeredAt;
        this.offeredBy = offeredBy;
    }
}
