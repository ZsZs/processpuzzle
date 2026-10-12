package com.processpuzzle.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One release of an organization: when it happened and who made it. What it carried is in
 * {@link OrgReleaseItem}s of the same number. A record only — the released definitions themselves are
 * the features' own rows, and v1 keeps no earlier revision to roll back to.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "org_releases",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "release_number"}))
public class OrgRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    /** Counts the organization's releases from 1; the unique constraint turns a concurrent release into a conflict. */
    @Column(name = "release_number", nullable = false, updatable = false)
    private long releaseNumber;

    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    @Column(name = "released_by", updatable = false)
    private String releasedBy;

    public OrgRelease(String orgKey, long releaseNumber, Instant releasedAt, String releasedBy) {
        this.orgKey = orgKey;
        this.releaseNumber = releaseNumber;
        this.releasedAt = releasedAt;
        this.releasedBy = releasedBy;
    }
}
