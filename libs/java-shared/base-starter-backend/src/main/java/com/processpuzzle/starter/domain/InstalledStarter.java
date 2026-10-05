package com.processpuzzle.starter.domain;

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

/** A starter installed in one organization, at the version last imported. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "starter_installed",
        uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "starter_id"}))
public class InstalledStarter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(name = "starter_id", nullable = false, updatable = false)
    private String starterId;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private Instant installedAt;

    private String installedBy;

    public InstalledStarter(String orgKey, String starterId) {
        this.orgKey = orgKey;
        this.starterId = starterId;
    }

    public void recordInstall(String version, Instant installedAt, String installedBy) {
        this.version = version;
        this.installedAt = installedAt;
        this.installedBy = installedBy;
    }
}
