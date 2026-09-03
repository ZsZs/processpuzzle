package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * What a tenant is currently entitled to, and for which period.
 *
 * <p>{@code orgKey} is a plain indexed column, not a JPA association to {@link Organization}, and
 * {@code planCode} likewise not one to {@link Plan}. Deliberate: a subscription must survive its
 * tenant's deletion long enough for the final invoice to reference it, and it must keep naming a plan
 * that has since been withdrawn from the catalog. Both would be impossible under a foreign key with
 * a cascade, and a nullable association would be a worse lie than a string.
 */
@Entity
@Table(name = "billing_subscriptions", indexes = @Index(name = "ix_subscription_org", columnList = "org_key"))
public class Subscription {

    @Id
    @Column(length = 63)
    private String id;

    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Column(name = "plan_code", nullable = false, length = 63)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
        // required by JPA
    }

    public Subscription(String id, String orgKey, String planCode, SubscriptionStatus status,
                        Instant currentPeriodStart, Instant currentPeriodEnd) {
        this.id = id;
        this.orgKey = orgKey;
        this.planCode = planCode;
        this.status = status == null ? SubscriptionStatus.TRIALING : status;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOrgKey() {
        return orgKey;
    }

    public String getPlanCode() {
        return planCode;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(Instant canceledAt) {
        this.canceledAt = canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
