package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * A ceiling one plan places on one metric.
 *
 * <p>An {@code @Embeddable} in an {@code @ElementCollection} rather than a {@code Map<UsageMetric,
 * Long>}: a plan's limits are shown as a list in the UI and exported in the contract as an array, and
 * a map column would have to be reordered into one anyway. An absent metric means unmetered on that
 * plan; a limit of {@code 0} means the feature is unavailable — two different things, which is why
 * absence is not modelled as zero.
 */
@Embeddable
public class PlanLimit {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UsageMetric metric;

    @Column(name = "max_quantity", nullable = false)
    private long maxQuantity;

    protected PlanLimit() {
        // required by JPA
    }

    public PlanLimit(UsageMetric metric, long maxQuantity) {
        this.metric = metric;
        this.maxQuantity = maxQuantity;
    }

    public UsageMetric getMetric() {
        return metric;
    }

    public long getMaxQuantity() {
        return maxQuantity;
    }
}
