package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One metric's measured quantity for one tenant over one period.
 *
 * <p>A record per (tenant, metric, period) rather than a running counter on the subscription: an
 * invoice has to be reproducible from what was measured at the time, and a counter that is updated in
 * place cannot answer what last month's figure was after this month's has been written.
 *
 * <p>Nothing in this platform produces these yet — there are no collectors. The entity, its
 * repository and its projection exist so the billing screens have a real shape rather than a
 * placeholder, and so that adding a collector is a new writer rather than a new model.
 */
@Entity
@Table(name = "billing_usage_records",
        indexes = @Index(name = "ix_usage_org_period", columnList = "org_key,period_start"))
public class UsageRecord {

    @Id
    @Column(length = 63)
    private String id;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UsageMetric metric;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected UsageRecord() {
        // required by JPA
    }

    public UsageRecord(String id, String orgKey, UsageMetric metric, long quantity,
                       Instant periodStart, Instant periodEnd) {
        this.id = id;
        this.orgKey = orgKey;
        this.metric = metric;
        this.quantity = quantity;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    @PrePersist
    void onCreate() {
        this.recordedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public UsageMetric getMetric() {
        return metric;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
