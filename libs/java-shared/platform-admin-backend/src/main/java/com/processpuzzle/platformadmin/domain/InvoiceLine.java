package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One charge on an invoice.
 *
 * <p>{@code amountMinor} is stored rather than computed from {@code quantity * unitAmountMinor}. An
 * issued invoice is a historical document: a rounding rule or a discount applied when it was written
 * must not be recomputed differently later, and a total that changes because the code changed is a
 * defect an accountant discovers rather than a test does.
 *
 * <p>{@code metric} is nullable, because not every line is metered — a flat plan fee has no metric.
 */
@Embeddable
public class InvoiceLine {

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UsageMetric metric;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_amount_minor", nullable = false)
    private long unitAmountMinor;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected InvoiceLine() {
        // required by JPA
    }

    public InvoiceLine(String description, UsageMetric metric, long quantity,
                       long unitAmountMinor, long amountMinor) {
        this.description = description;
        this.metric = metric;
        this.quantity = quantity;
        this.unitAmountMinor = unitAmountMinor;
        this.amountMinor = amountMinor;
    }

    public String getDescription() {
        return description;
    }

    public UsageMetric getMetric() {
        return metric;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getUnitAmountMinor() {
        return unitAmountMinor;
    }

    public long getAmountMinor() {
        return amountMinor;
    }
}
