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

    /**
     * Whether it was installed as a domain starter (manifestVersion 3), which is added to the organization and can be
     * removed again, not as one that replaced the organization's definitions. Null for a row written before the flag:
     * those replaced.
     */
    private Boolean domainStarter;

    public InstalledStarter(String orgKey, String starterId) {
        this.orgKey = orgKey;
        this.starterId = starterId;
    }

    public boolean isDomainStarter() {
        return Boolean.TRUE.equals(domainStarter);
    }

    public void markDomainStarter() {
        this.domainStarter = true;
    }

    public void recordInstall(String version, Instant installedAt, String installedBy) {
        this.version = version;
        this.installedAt = installedAt;
        this.installedBy = installedBy;
    }
}
